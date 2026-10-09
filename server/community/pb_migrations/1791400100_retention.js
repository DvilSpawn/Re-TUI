migrate((app) => {
    const messages = app.findCollectionByNameOrId("messages");
    const active = "@request.auth.collectionName = 'members' && @request.auth.active = true";
    const fresh = "created > strftime('%Y-%m-%d %H:%M:%fZ', @now, '-24 hours') && created <= @now";
    messages.listRule = active + " && " + fresh;
    messages.viewRule = messages.listRule;
    app.save(messages);
});
