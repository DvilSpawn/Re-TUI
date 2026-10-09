migrate((app) => {
    const members=app.findCollectionByNameOrId("members");
    app.save(new Collection({name:"presence",type:"base",listRule:null,viewRule:null,createRule:null,updateRule:null,deleteRule:null,
        fields:[{name:"member",type:"relation",collectionId:members.id,required:true,maxSelect:1,cascadeDelete:true},
            {name:"issued_at",type:"date",required:true},{name:"expires_at",type:"date",required:true}],
        indexes:["CREATE UNIQUE INDEX idx_presence_member ON presence (member)","CREATE INDEX idx_presence_expiry ON presence (expires_at)"]}));
    const settings=app.settings();
    for (const label of ["GET /api/community/users","GET /api/community/mentions","POST /api/community/presence"]) settings.rateLimits.rules.push({label:label,audience:"",duration:60,maxRequests:label.startsWith("POST")?120:240});
    app.save(settings);
});
