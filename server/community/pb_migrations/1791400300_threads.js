migrate((app) => {
    const members = app.findCollectionByNameOrId("members");
    const active = "@request.auth.collectionName = 'members' && @request.auth.active = true";
    const fields = [
        {name:"author",type:"relation",collectionId:members.id,required:true,maxSelect:1,cascadeDelete:true},
        {name:"author_handle",type:"text",required:true,max:24},
        {name:"body",type:"text",required:true,max:2000},
        {name:"client_id",type:"text",required:true,pattern:"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"},
        {name:"created",type:"autodate",onCreate:true,onUpdate:false},
        {name:"updated",type:"autodate",onCreate:true,onUpdate:true}
    ];
    const threads = new Collection({name:"threads",type:"base",listRule:active,viewRule:active,
        createRule:active+" && author = @request.auth.id",updateRule:null,deleteRule:null,
        fields:fields.concat([
            {name:"channel",type:"select",required:true,maxSelect:1,values:["bugs","features","presets"]},
            {name:"title",type:"text",required:true,max:480}
        ]),indexes:["CREATE UNIQUE INDEX idx_threads_client ON threads (author,client_id)","CREATE INDEX idx_threads_channel_cursor ON threads (channel,created DESC,id DESC)"]});
    app.save(threads);
    app.save(new Collection({name:"thread_replies",type:"base",listRule:active,viewRule:active,
        createRule:active+" && author = @request.auth.id",updateRule:null,deleteRule:null,
        fields:fields.concat([{name:"thread",type:"relation",collectionId:threads.id,required:true,maxSelect:1,cascadeDelete:true}]),
        indexes:["CREATE UNIQUE INDEX idx_replies_client ON thread_replies (author,client_id)","CREATE INDEX idx_replies_cursor ON thread_replies (thread,created,id)"]}));
    const settings = app.settings();
    for (const name of ["threads","thread_replies"]) settings.rateLimits.rules.push({label:name+":create",audience:"",duration:60,maxRequests:20});
    app.save(settings);
});
