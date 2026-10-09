cronAdd("community_retention", "* * * * *", () => {
    const cutoff = new Date(Date.now() - 86400000).toISOString().replace("T", " ");
    // Bounded batches avoid loading the full feed into memory.
    while (true) {
        const records = $app.findRecordsByFilter("messages", "created <= {:cutoff}", "created", 100, 0, {cutoff: cutoff});
        if (!records.length) break;
        for (const record of records) $app.delete(record);
    }
    const now = new Date().toISOString().replace("T", " ");
    for (const collection of ["reports","moderator_requests","presence"]) {
        while (true) {
            const records = $app.findRecordsByFilter(collection,"expires_at <= {:now}","expires_at",100,0,{now:now});
            if (!records.length) break;
            for (const record of records) $app.delete(record);
        }
    }
});
