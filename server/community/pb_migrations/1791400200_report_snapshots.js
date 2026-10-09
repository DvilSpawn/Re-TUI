migrate((app) => {
    const reports = app.findCollectionByNameOrId("reports");
    const message = reports.fields.getByName("message");
    message.required = false;
    message.cascadeDelete = false;
    const reporter = reports.fields.getByName("reporter");
    reporter.required = false;
    reporter.cascadeDelete = false;
    reports.fields.add(new TextField({name:"source_message_id", max:15}));
    reports.fields.add(new TextField({name:"source_reporter_id", max:15}));
    reports.fields.add(new TextField({name:"snapshot_body", max:2000}));
    reports.fields.add(new TextField({name:"snapshot_author_handle", max:24}));
    reports.fields.add(new DateField({name:"expires_at", required:true}));
    reports.indexes = ["CREATE INDEX idx_reports_expiry ON reports (expires_at)"];
    app.save(reports);
    // Backfill before the revised message purge can run; no cascade loses evidence.
    for (const record of app.findAllRecords("reports")) {
        const source = app.findRecordById("messages", record.getString("message"));
        record.set("source_message_id", source.id);
        record.set("source_reporter_id", record.getString("reporter"));
        record.set("snapshot_body", source.getString("body"));
        record.set("snapshot_author_handle", source.getString("author_handle"));
        const date = new Date(record.getString("created").replace(" ","T"));
        if (!Number.isFinite(date.getTime())) throw new Error("Invalid report creation timestamp");
        const day = date.getUTCDate();
        date.setUTCDate(1); date.setUTCMonth(date.getUTCMonth()+6);
        const last = new Date(Date.UTC(date.getUTCFullYear(), date.getUTCMonth()+1,0)).getUTCDate();
        date.setUTCDate(Math.min(day,last));
        record.set("expires_at", date.toISOString());
        app.save(record);
    }
    reports.indexes.push("CREATE UNIQUE INDEX idx_reports_source ON reports (source_reporter_id, source_message_id)");
    app.save(reports);
});
