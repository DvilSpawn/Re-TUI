const policy = "2026-10-10";
function moderator(e) {
    const m = require(`${__hooks}/community-v3.js`).member(e);
    if (!m.getBool("moderator") || m.getString("handle") !== "dvil") throw new ForbiddenError("Moderator required");
    return m;
}
function body(e, keys) {
    const b = e.requestInfo().body;
    if (Object.keys(b).some(k => !keys.includes(k))) throw new BadRequestError("Unexpected field");
    return b;
}
function accepted(app, auth) {
    if (!auth || auth.collection().name !== "members") throw new UnauthorizedError("Sign in required");
    const m = app.findRecordById("members",auth.id);
    if (!m.getBool("active")) throw new ForbiddenError("Account inactive");
    if (m.getString("policy_version") !== policy) throw new ApiError(428,"Accept the current community rules before posting");
    return m;
}
function fresh(record) {
    if (record.collection().name === "messages") {
        const created = Date.parse(record.getString("created").replace(" ","T"));
        if (!Number.isFinite(created) || created <= Date.now()-86400000 || created > Date.now()) throw new NotFoundError("Source expired");
    }
}
module.exports = {policy:policy,moderator:moderator,body:body,accepted:accepted,fresh:fresh};
