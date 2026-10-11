onRecordCreateRequest((e) => {
    if (!e.hasSuperuserAuth()) require(`${__hooks}/release-gates.js`).accepted(e.app,e.auth);
    if (["reports","moderator_requests"].includes(e.collection.name) && e.record.getString("message")) {
        let source;
        try { source = e.app.findRecordById("messages",e.record.getString("message")); }
        catch (_) { throw new BadRequestError("Message missing or expired"); }
        e.record.set("source_collection","messages"); e.record.set("source_author_id",source.getString("author"));
    }
    e.next();
}, "messages","threads","thread_replies","reports","moderator_requests");

routerAdd("POST","/api/community/policy",(e) => {
    const gates = require(`${__hooks}/release-gates.js`);
    const m = require(`${__hooks}/community-v3.js`).member(e);
    const b = gates.body(e,["version"]);
    if (b.version !== gates.policy) throw new BadRequestError("Unknown community policy");
    m.set("policy_version",gates.policy); m.set("policy_accepted_at",new DateTime(new Date().toISOString())); e.app.save(m);
    return e.json(200,{version:gates.policy});
},$apis.requireAuth("members"));

routerAdd("POST","/api/community/report",(e) => {
    const gates = require(`${__hooks}/release-gates.js`);
    const m = gates.accepted(e.app,e.auth);
    const b = gates.body(e,["collection","id","reason"]);
    if (!["messages","threads","thread_replies"].includes(b.collection) || !/^[a-z0-9]{15}$/.test(b.id || "")) throw new BadRequestError("Invalid source");
    const source = e.app.findRecordById(b.collection,b.id); gates.fresh(source);
    const r = new Record(e.app.findCollectionByNameOrId("moderator_requests"));
    r.set("reporter",m.id); r.set("kind","report"); r.set("body",b.reason);
    r.set("source_collection",b.collection); r.set("source_message_id",source.id); r.set("source_author_id",source.getString("author"));
    r.set("snapshot_body",source.getString("body")); r.set("snapshot_author_handle",source.getString("author_handle"));
    const created = new Date().toISOString(); r.setRaw("created",new DateTime(created));
    r.set("expires_at",require(`${__hooks}/retention.js`).sixMonths(created));
    e.app.save(r);
    return e.json(200,{id:r.id});
},$apis.requireAuth("members"));

routerAdd("GET","/api/community/moderation",(e) => {
    require(`${__hooks}/release-gates.js`).moderator(e);
    const paging = require(`${__hooks}/community-v3.js`).paging(e,50,["page","perPage","collection"]);
    const collection = paging.query.collection ? paging.query.collection[0] : "moderator_requests";
    if (!["reports","moderator_requests"].includes(collection)) throw new BadRequestError("Invalid queue");
    const rows = e.app.findRecordsByFilter(collection,"expires_at > @now && resolved_at = ''","created,id",paging.perPage+1,(paging.page-1)*paging.perPage);
    let operations = {healthy:false,alerts:["monitor_status_unavailable"]};
    try {
        operations = JSON.parse(toString($os.readFile("/var/lib/retui-community/operations.json")));
        if (Date.parse(operations.checked_at) < Date.now()-15*60000) operations={healthy:false,alerts:["monitor_status_stale"]};
    } catch (_) {}
    return e.json(200,{page:paging.page,hasMore:rows.length>paging.perPage,operations:operations,items:rows.slice(0,paging.perPage).map(r=>({
        id:r.id,queue:collection,body:r.getString("body") || r.getString("reason"),created:r.getString("created"),
        source_collection:r.getString("source_collection") || (r.getString("source_message_id") ? "messages" : ""),
        source_id:r.getString("source_message_id"),snapshot_body:r.getString("snapshot_body"),author_handle:r.getString("snapshot_author_handle")
    }))});
},$apis.requireAuth("members"));

routerAdd("POST","/api/community/moderation/action",(e) => {
    const gates = require(`${__hooks}/release-gates.js`); const mod = gates.moderator(e);
    const b = gates.body(e,["queue","id","action"]);
    if (!["reports","moderator_requests"].includes(b.queue) || !/^[a-z0-9]{15}$/.test(b.id || "") || !["dismiss","remove","ban"].includes(b.action)) throw new BadRequestError("Invalid action");
    let result;
    e.app.runInTransaction((app) => {
        const current = app.findRecordById("members",mod.id);
        if (!current.getBool("active") || !current.getBool("moderator") || current.getString("handle") !== "dvil") throw new ForbiddenError("Moderator required");
        const r = app.findRecordById(b.queue,b.id);
        if (Date.parse(r.getString("expires_at").replace(" ","T")) <= Date.now()) throw new NotFoundError("Request expired");
        if (r.getString("resolved_at")) { result={resolved:true,action:r.getString("resolution")}; return; }
        const collection = r.getString("source_collection") || "messages";
        if (!["messages","threads","thread_replies"].includes(collection)) throw new BadRequestError("Invalid source");
        let source = null;
        if (r.getString("source_message_id")) {
            try { source = app.findRecordById(collection,r.getString("source_message_id")); } catch (_) {}
        }
        if (b.action === "remove" && source) app.delete(source);
        if (b.action === "ban") {
            const authorId = r.getString("source_author_id") || (source ? source.getString("author") : "");
            if (!authorId || authorId === mod.id) throw new BadRequestError("Cannot ban this author");
            const author = app.findRecordById("members",authorId);
            author.set("active",false); app.save(author);
        }
        r.set("resolved_at",new DateTime(new Date().toISOString())); r.set("resolved_by",mod.id); r.set("resolution",b.action); app.save(r);
        result={resolved:true,action:b.action};
    });
    return e.json(200,result);
},$apis.requireAuth("members"));

routerAdd("POST","/api/community/account-delete",(e) => {
    const gates = require(`${__hooks}/release-gates.js`);
    // Inactive accounts may delete themselves; the password, not posting permission, authorizes deletion.
    const b = gates.body(e,["password","confirm"]);
    if (typeof b.password !== "string" || b.password.length > 300 || typeof b.confirm !== "string") throw new BadRequestError("Confirm account deletion");
    e.app.runInTransaction((app) => {
        let m;
        try { m = app.findFirstRecordByData("members","handle",b.confirm); } catch (_) { throw new BadRequestError("Invalid confirmation or password"); }
        if (e.auth && (e.auth.collection().name !== "members" || e.auth.id !== m.id)) throw new BadRequestError("Invalid confirmation or password");
        if (b.confirm !== m.getString("handle") || !m.validatePassword(b.password)) throw new BadRequestError("Invalid confirmation or password");
        app.delete(m);
    });
    return e.json(200,{deleted:true});
});
