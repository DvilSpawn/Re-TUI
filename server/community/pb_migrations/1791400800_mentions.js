migrate((app) => {
    const members=app.findCollectionByNameOrId("members");
    const fields=[{name:"recipient",type:"relation",collectionId:members.id,required:true,maxSelect:1,cascadeDelete:true},
        {name:"kind",type:"select",required:true,maxSelect:1,values:["message","thread","thread_reply"]},
        {name:"created",type:"autodate",onCreate:true,onUpdate:false}];
    for (const name of ["messages","threads","thread_replies"]) fields.push({name:name,type:"relation",collectionId:app.findCollectionByNameOrId(name).id,maxSelect:1,cascadeDelete:true});
    app.save(new Collection({name:"mentions",type:"base",listRule:null,viewRule:null,createRule:null,updateRule:null,deleteRule:null,fields:fields,
        indexes:["CREATE INDEX idx_mentions_recipient_cursor ON mentions (recipient,created DESC,id DESC)","CREATE UNIQUE INDEX idx_mentions_source_target ON mentions (recipient,messages,threads,thread_replies)"]}));
});
