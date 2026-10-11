migrate((app) => {
    const members = app.findCollectionByNameOrId("members");
    members.fields.add(new TextField({name:"policy_version",max:32}));
    members.fields.add(new DateField({name:"policy_accepted_at"}));
    app.save(members);
    for (const collectionName of ["reports","moderator_requests"]) {
        const c = app.findCollectionByNameOrId(collectionName);
        c.fields.add(new TextField({name:"source_collection",max:32}));
        c.fields.add(new TextField({name:"source_author_id",max:15}));
        c.fields.add(new DateField({name:"resolved_at"}));
        c.fields.add(new TextField({name:"resolved_by",max:15}));
        c.fields.add(new TextField({name:"resolution",max:16}));
        c.listRule = "@request.auth.collectionName = 'members' && @request.auth.active = true && @request.auth.moderator = true && @request.auth.handle = 'dvil' && expires_at > @now";
        c.viewRule = c.listRule;
        app.save(c);
    }
    const moderators = app.findRecordsByFilter("members","moderator = true || handle = 'dvil'","",0);
    for (const record of moderators) {
        record.set("moderator",record.getString("handle") === "dvil");
        app.save(record);
    }
    const settings = app.settings();
    for (const route of ["POST /api/community/policy","POST /api/community/report","POST /api/community/moderation/action","POST /api/community/account-delete"]) {
        settings.rateLimits.rules.push({label:route,audience:"",duration:60,maxRequests:route.endsWith("/policy") || route.endsWith("/action") ? 20 : 5});
    }
    app.save(settings);
});
