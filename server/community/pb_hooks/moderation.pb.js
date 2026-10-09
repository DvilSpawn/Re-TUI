onRecordUpdateRequest((e) => {
    // Administrator edits cannot extend retention from the immutable request creation date.
    const original = e.record.original();
    e.record.setRaw("created", original.getRaw("created"));
    for (const key of ["source_message_id","snapshot_body","snapshot_author_handle","source_reporter_id"]) e.record.set(key,original.get(key));
    e.record.set("expires_at", require(`${__hooks}/retention.js`).sixMonths(original.getString("created")));
    e.next();
}, "reports", "moderator_requests");

onRecordCreateRequest((e) => {
    if (!e.hasSuperuserAuth()) {
        for (const key of Object.keys(e.requestInfo().body)) {
            if (!["reporter","kind","body","message"].includes(key)) throw new BadRequestError("Unexpected moderator request field");
        }
        if (!e.auth || e.auth.collection().name !== "members" || !e.auth.getBool("active") || e.record.getString("reporter") !== e.auth.id) {
            throw new BadRequestError("Invalid reporter");
        }
    }
    const retention = require(`${__hooks}/retention.js`);
    if (e.record.getString("kind") === "report" && !e.record.getString("message")) throw new BadRequestError("Report requires message");
    if (e.record.getString("message")) retention.snapshot(e.app,e.record);
    const created = new Date().toISOString();
    e.record.setRaw("created", new DateTime(created));
    e.record.set("expires_at",retention.sixMonths(created));
    e.next();
}, "moderator_requests");

onRecordValidate((e) => {
    const body = e.record.getString("body");
    if (!body.trim() || Array.from(body).length > 500 || unescape(encodeURIComponent(body)).length > 2000) throw new BadRequestError("Request must contain 1–500 code points / 2000 bytes");
    e.next();
}, "moderator_requests");

onRecordsListRequest((e) => {
    if (!e.hasSuperuserAuth()) {
        if (!e.auth) throw new UnauthorizedError("Sign in required");
        if (e.auth.collection().name !== "members" || !e.auth.getBool("active") || !e.auth.getBool("moderator")) throw new ForbiddenError("Moderator required");
    }
    if (Number(e.requestInfo().query["perPage"]) > 50) throw new BadRequestError("perPage must be at most 50");
    e.next();
}, "reports", "moderator_requests");
