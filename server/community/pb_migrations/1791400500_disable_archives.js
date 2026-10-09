migrate((app) => {
    const settings = app.settings();
    // Whole-database archives preserve expired message/request bodies. No new scheduled copies.
    settings.backups.cron = "";
    app.save(settings);
});
