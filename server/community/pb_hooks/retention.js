module.exports = {
    sixMonths(value) {
        const date = new Date(String(value).replace(" ", "T"));
        if (!Number.isFinite(date.getTime())) throw new BadRequestError("Invalid creation timestamp");
        const day = date.getUTCDate();
        date.setUTCDate(1); date.setUTCMonth(date.getUTCMonth()+6);
        const last = new Date(Date.UTC(date.getUTCFullYear(),date.getUTCMonth()+1,0)).getUTCDate();
        date.setUTCDate(Math.min(day,last));
        return date.toISOString();
    },
    snapshot(app, record) {
        let source;
        try {
            source = app.findRecordById("messages", record.getString("message"));
        } catch (_) {
            throw new BadRequestError("Message missing or expired");
        }
        const age = Date.now() - Date.parse(source.getString("created"));
        if (!Number.isFinite(age) || age < 0 || age >= 86400000) throw new BadRequestError("Message expired");
        record.set("source_message_id", source.id);
        record.set("snapshot_body", source.getString("body"));
        record.set("snapshot_author_handle", source.getString("author_handle"));
    }
};
