routerAdd("POST", "/api/community/presence", (e) => {
    const current=require(`${__hooks}/community-v3.js`).member(e);
    const body=e.requestInfo().body;
    if (Object.keys(body).length!==1 || typeof body.active!=="boolean") throw new BadRequestError("Only active boolean is accepted");
    return e.app.runInTransaction((app)=> {
        const records=app.findRecordsByFilter("presence","member={:id}","",1,0,{id:current.id});
        if (!body.active) {
            for (const record of records) app.delete(record);
            return e.json(200,{online:false,expires_at:""});
        }
        const record=records.length ? records[0] : new Record(app.findCollectionByNameOrId("presence"));
        const now=Date.now();
        record.set("member",current.id);
        record.set("issued_at",new Date(now).toISOString());
        record.set("expires_at",new Date(now+90000).toISOString());
        app.save(record);
        return e.json(200,{online:true,expires_at:record.getString("expires_at")});
    });
});
