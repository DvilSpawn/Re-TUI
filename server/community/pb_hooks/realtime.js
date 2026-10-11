function authorized(app, client) {
    const member = app.findAuthRecordByToken(client.get("community_token"), "auth");
    if (member.collection().name !== "members" || !member.getBool("active")) throw new ForbiddenError("Account inactive");
    return member;
}
function publish(e, action) {
    // After-success may carry a completed transaction; authorization needs the root DB.
    const app = e.app.store().get("community_root_app");
    const clients = app.subscriptionsBroker().clients();
    for (const id of Object.keys(clients)) {
        const client = clients[id];
        if (!client.hasSubscription("community")) continue;
        try {
            const member = authorized(app, client);
            // Delete notifications contain no record metadata or expired body.
            let payload = {action:"invalidate"};
            if (action !== "delete") {
                const info = new RequestInfo({auth:member});
                if (e.record.collection().viewRule === null) continue;
                if (!app.canAccessRecord(e.record, info, e.record.collection().viewRule)) continue;
                if (e.record.collection().name === "messages") {
                    const created = Date.parse(e.record.getString("created").replace(" ", "T"));
                    if (!Number.isFinite(created) || created <= Date.now() - 86400000 || created > Date.now()) continue;
                }
                payload = {action:action,collection:e.record.collection().name,id:e.record.id};
            }
            client.send(new SubscriptionMessage({name:"community",data:JSON.stringify(payload)}));
        } catch (_) { client.discard(); }
    }
}
module.exports = {authorized:authorized,publish:publish};
