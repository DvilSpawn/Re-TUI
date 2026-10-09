// Query middleware executes before native CRUD queries (list request hooks execute after).
routerUse((e) => {
    // Native superuser expansion bypasses related-record view rules and expiry hooks.
    // This API uses explicit record reads, never relation expansion (including auth/mutations).
    const parameters = JSON.parse(toString(e.request.url.query()));
    if (Object.prototype.hasOwnProperty.call(parameters, "expand")) {
        throw new BadRequestError("Relation expansion is disabled");
    }
    if (e.request.method === "GET") {
        const match = /^\/api\/collections\/([^/]+)\/records$/.exec(e.request.url.path);
        if (match) {
            const collection = e.app.findCollectionByNameOrId(match[1]);
            let fresh = "";
            if (collection.name === "messages") fresh = "created > strftime('%Y-%m-%d %H:%M:%fZ', @now, '-24 hours') && created <= @now";
            if (["reports","moderator_requests"].includes(collection.name)) fresh = "expires_at > @now";
            if (fresh) {
                const query = e.request.url.query();
                const supplied = query.get("filter");
                query.set("filter", "(" + fresh + ")" + (supplied ? " && (" + supplied + ")" : ""));
                e.request.url.rawQuery = query.encode();
            }
        }
    }
    return e.next();
});

onRecordViewRequest((e) => {
    const expires = Date.parse(e.record.getString("expires_at").replace(" ","T"));
    if (!Number.isFinite(expires) || expires <= Date.now()) throw new NotFoundError("Request expired");
    e.next();
}, "reports", "moderator_requests");

onRecordViewRequest((e) => {
    const timestamp = Date.parse(e.record.getString("created").replace(" ","T"));
    if (!Number.isFinite(timestamp) || timestamp <= Date.now() - 86400000 || timestamp > Date.now()) {
        throw new NotFoundError("Message expired");
    }
    e.next();
}, "messages");

onRecordsListRequest((e) => {
    if (!e.hasSuperuserAuth()) {
        if (!e.auth) throw new UnauthorizedError("Sign in required");
        if (e.auth.collection().name !== "members" || !e.auth.getBool("active")) throw new ForbiddenError("Account inactive");
    }
    const pageSize = e.requestInfo().query["perPage"];
    if (pageSize && Number(pageSize) > 50) throw new BadRequestError("perPage must be at most 50");
    e.next();
}, "messages", "threads", "thread_replies");
