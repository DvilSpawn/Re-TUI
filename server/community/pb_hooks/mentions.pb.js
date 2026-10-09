onRecordCreate((e)=> {
    return e.app.runInTransaction((app)=> {
        e.app=app;
        const ids=require(`${__hooks}/mentions.js`).targets(app,e.record);
        e.next();
        const name=e.record.collection().name;
        for (const id of ids) {
            const mention=new Record(app.findCollectionByNameOrId("mentions"));
            mention.set("recipient",id);
            mention.set("kind",{messages:"message",threads:"thread",thread_replies:"thread_reply"}[name]);
            mention.set(name,e.record.id);
            app.save(mention);
        }
    });
},"messages","threads","thread_replies");

routerAdd("GET","/api/community/mentions",(e)=> {
    const api=require(`${__hooks}/community-v3.js`);
    const current=api.member(e);
    const p=api.paging(e,50,["page","perPage"]);
    const items=[];
    let skip=(p.page-1)*p.perPage;
    // Bound source/rule lookups; reaching the ceiling is not proof of exhaustion.
    for (let offset=0;offset<=1000;offset+=100) {
        const records=e.app.findRecordsByFilter("mentions","recipient={:id}","-created,-id",offset===1000?1:100,offset,{id:current.id});
        if (offset===1000 && records.length) throw new ApiError(503,"Mention scan limit exceeded.");
        for (const record of records) {
            const kind=record.getString("kind");
            const name={message:"messages",thread:"threads",thread_reply:"thread_replies"}[kind];
            if (!name) continue;
            let source;
            try { source=e.app.findRecordById(name,record.getString(name)); } catch (_) { continue; }
            if (!e.app.canAccessRecord(source,e.requestInfo(),source.collection().viewRule)) continue;
            let threadId="",channel="general",expires="";
            const created=source.getString("created");
            if (kind==="message") {
                const stamp=Date.parse(created.replace(" ","T"));
                if (!Number.isFinite(stamp) || stamp>Date.now() || stamp<=Date.now()-86400000) continue;
                expires=new Date(stamp+86400000).toISOString().replace("T"," ");
            } else {
                let root=source;
                if (kind==="thread_reply") {
                    try { root=e.app.findRecordById("threads",source.getString("thread")); } catch (_) { continue; }
                    if (!e.app.canAccessRecord(root,e.requestInfo(),root.collection().viewRule)) continue;
                }
                threadId=root.id; channel=root.getString("channel");
            }
            if (skip>0) { skip--; continue; }
            items.push({id:record.id,kind:kind,source_id:source.id,thread_id:threadId,channel:channel,author_handle:source.getString("author_handle"),created:created,expires_at:expires});
            if (items.length>p.perPage) return e.json(200,{page:p.page,perPage:p.perPage,hasMore:true,items:items.slice(0,p.perPage)});
        }
        if (records.length<100) break;
    }
    return e.json(200,{page:p.page,perPage:p.perPage,hasMore:false,items:items});
});
