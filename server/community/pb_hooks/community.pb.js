onRecordCreateRequest((e) => {
    if (!e.hasSuperuserAuth()) {
        for (const key of Object.keys(e.requestInfo().body)) {
            if (!["handle", "password", "passwordConfirm"].includes(key)) {
                throw new BadRequestError("Unexpected registration field");
            }
        }
    }
    e.record.set("handle", e.record.getString("handle").toLowerCase());
    if (!e.hasSuperuserAuth()) {
        e.record.set("active", true);
        e.record.set("moderator", false);
    }
    e.next();
}, "members");

onRecordCreateRequest((e) => {
    if (!e.hasSuperuserAuth()) {
        const input = e.requestInfo().body;
        const allowed = ["author", "body", "client_id"];
        if (e.collection.name === "threads") allowed.push("channel", "title");
        if (e.collection.name === "thread_replies") allowed.push("thread");
        for (const key of Object.keys(input)) {
            if (!allowed.includes(key)) {
                throw new BadRequestError("Unexpected message field");
            }
        }
        if (!e.auth || e.auth.collection().name !== "members" || !e.auth.getBool("active") || e.record.getString("author") !== e.auth.id) {
            throw new BadRequestError("Invalid message author");
        }
        e.record.set("author_handle", e.auth.getString("handle"));
    }
    e.next();
}, "messages", "threads", "thread_replies");

onRecordValidate((e) => {
    const text = e.record.getString("body");
    if (!text.trim() || Array.from(text).length > 500 || unescape(encodeURIComponent(text)).length > 2000) {
        throw new BadRequestError("Message must contain 1–500 code points and at most 2000 UTF-8 bytes");
    }
    if (e.record.collection().name === "threads") {
        const title = e.record.getString("title");
        if (!title.trim() || Array.from(title).length > 120 || unescape(encodeURIComponent(title)).length > 480) {
            throw new BadRequestError("Title must contain 1–120 code points / 480 bytes");
        }
    }
    e.next();
}, "messages", "threads", "thread_replies");

onRecordCreateRequest((e) => {
    if (!e.hasSuperuserAuth()) {
        for (const key of Object.keys(e.requestInfo().body)) {
            if (!["reporter", "message", "reason"].includes(key)) throw new BadRequestError("Unexpected report field");
        }
    }
    const retention = require(`${__hooks}/retention.js`);
    retention.snapshot(e.app, e.record);
    e.record.set("source_reporter_id", e.record.getString("reporter"));
    const created = new Date().toISOString();
    e.record.setRaw("created", new DateTime(created));
    e.record.set("expires_at", retention.sixMonths(created));
    e.next();
}, "reports");

onRecordValidate((e) => {
    const text = e.record.getString("reason");
    if (!text.trim() || Array.from(text).length > 500 || unescape(encodeURIComponent(text)).length > 2000) {
        throw new BadRequestError("Report must contain 1–500 code points and at most 2000 UTF-8 bytes");
    }
    e.next();
}, "reports");
