migrate((app) => {
    const threads = app.findCollectionByNameOrId("threads");
    threads.fields.getByName("channel").values = ["bugs","features","presets","help"];
    app.save(threads);
});
