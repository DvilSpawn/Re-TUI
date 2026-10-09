migrate((app) => {
    const members = app.findCollectionByNameOrId("members");
    members.fields.add(new BoolField({name:"moderator"}));
    app.save(members);
    const active = "@request.auth.collectionName = 'members' && @request.auth.active = true";
    const moderator = active + " && @request.auth.moderator = true && expires_at > @now";
    const messages = app.findCollectionByNameOrId("messages");
    app.save(new Collection({name:"moderator_requests",type:"base",listRule:moderator,viewRule:moderator,
        createRule:active+" && reporter = @request.auth.id",updateRule:null,deleteRule:null,
        fields:[
            {name:"reporter",type:"relation",collectionId:members.id,required:false,maxSelect:1,cascadeDelete:false},
            {name:"kind",type:"select",required:true,maxSelect:1,values:["report","moderate"]},
            {name:"body",type:"text",required:true,max:2000},
            {name:"message",type:"relation",collectionId:messages.id,required:false,maxSelect:1,cascadeDelete:false},
            {name:"source_message_id",type:"text",max:15},
            {name:"snapshot_body",type:"text",max:2000},
            {name:"snapshot_author_handle",type:"text",max:24},
            {name:"expires_at",type:"date",required:true},
            {name:"created",type:"autodate",onCreate:true,onUpdate:false},
            {name:"updated",type:"autodate",onCreate:true,onUpdate:true}
        ],indexes:["CREATE INDEX idx_mod_requests_expiry ON moderator_requests (expires_at)","CREATE INDEX idx_mod_requests_cursor ON moderator_requests (created,id)"]}));
    const reports = app.findCollectionByNameOrId("reports");
    reports.listRule = moderator; reports.viewRule = moderator;
    app.save(reports);
    const settings = app.settings();
    settings.rateLimits.rules.push({label:"moderator_requests:create",audience:"",duration:60,maxRequests:5});
    app.save(settings);
});
