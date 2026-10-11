// Capture the non-transactional app while hooks load, not inside an after-commit callback.
$app.store().set("community_root_app", $app);
onRealtimeConnectRequest((e) => {
    require(`${__hooks}/community-v3.js`).member(e);
    e.client.set("community_token", e.requestInfo().headers.authorization);
    e.idleTimeout = 60 * 1000000000;
    e.next();
});
onRealtimeSubscribeRequest((e) => {
    require(`${__hooks}/community-v3.js`).member(e);
    if (e.client.get("community_token") !== e.requestInfo().headers.authorization) throw new ForbiddenError("Connection identity mismatch");
    if (e.subscriptions.length > 1 || e.subscriptions.some((topic) => topic !== "community")) throw new BadRequestError("Only the community topic is supported");
    e.next();
});
onRealtimeMessageSend((e) => {
    if (e.message.name === "community") {
        try { require(`${__hooks}/realtime.js`).authorized(e.app,e.client); }
        catch (_) { e.client.discard(); return; }
    }
    e.next();
});
onRecordAfterCreateSuccess((e) => {
    e.next(); require(`${__hooks}/realtime.js`).publish(e,"create");
}, "messages", "threads", "thread_replies");
onRecordAfterUpdateSuccess((e) => {
    e.next(); require(`${__hooks}/realtime.js`).publish(e,"update");
}, "messages", "threads", "thread_replies");
onRecordAfterDeleteSuccess((e) => {
    e.next(); require(`${__hooks}/realtime.js`).publish(e,"delete");
}, "messages", "threads", "thread_replies");
