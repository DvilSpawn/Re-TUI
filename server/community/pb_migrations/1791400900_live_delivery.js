migrate((app) => {
    const settings = app.settings();
    for (const rule of settings.rateLimits.rules) {
        if (rule.label === "/api/") rule.maxRequests = 240;
    }
    settings.rateLimits.rules.push({label:"GET /api/realtime",audience:"",duration:60,maxRequests:20});
    settings.rateLimits.rules.push({label:"POST /api/realtime",audience:"",duration:60,maxRequests:30});
    app.save(settings);
});
