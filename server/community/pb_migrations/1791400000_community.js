migrate((app) => {
    const members = new Collection({
        name: "members", type: "auth", createRule: "", listRule: null, viewRule: null,
        updateRule: null, deleteRule: null, manageRule: null, authRule: "active = true",
        passwordAuth: {enabled: true, identityFields: ["handle"]},
        authToken: {duration: 86400},
        fields: [
            {name: "email", type: "email", required: false, hidden: true},
            {name: "handle", type: "text", required: true, min: 3, max: 24, pattern: "^[-_a-zA-Z0-9]{3,24}$"},
            {name: "active", type: "bool"},
        ],
        indexes: ["CREATE UNIQUE INDEX idx_members_handle ON members (handle COLLATE NOCASE)"],
    });
    app.save(members);
    const active = "@request.auth.collectionName = 'members' && @request.auth.active = true";
    const messages = new Collection({
        name: "messages", type: "base", listRule: active, viewRule: active,
        createRule: active + " && author = @request.auth.id", updateRule: null, deleteRule: null,
        fields: [
            {name: "author", type: "relation", collectionId: members.id, required: true, maxSelect: 1, cascadeDelete: true},
            {name: "author_handle", type: "text", required: true, max: 24},
            {name: "body", type: "text", required: true, max: 2000},
            {name: "client_id", type: "text", required: true, pattern: "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"},
            {name: "created", type: "autodate", onCreate: true, onUpdate: false},
            {name: "updated", type: "autodate", onCreate: true, onUpdate: true},
        ],
        indexes: ["CREATE UNIQUE INDEX idx_messages_client ON messages (author, client_id)", "CREATE INDEX idx_messages_cursor ON messages (created DESC, id DESC)"],
    });
    app.save(messages);
    app.save(new Collection({
        name: "reports", type: "base", listRule: null, viewRule: null, updateRule: null, deleteRule: null,
        createRule: active + " && reporter = @request.auth.id",
        fields: [
            {name: "reporter", type: "relation", collectionId: members.id, required: true, maxSelect: 1, cascadeDelete: true},
            {name: "message", type: "relation", collectionId: messages.id, required: true, maxSelect: 1, cascadeDelete: true},
            {name: "reason", type: "text", required: true, max: 2000},
            {name: "created", type: "autodate", onCreate: true, onUpdate: false},
            {name: "updated", type: "autodate", onCreate: true, onUpdate: true},
        ],
        indexes: ["CREATE UNIQUE INDEX idx_reports_member_message ON reports (reporter, message)"],
    }));
    const password = $os.getenv("RETUI_BOOTSTRAP_PASSWORD");
    if (password) {
        const admin = new Record(app.findCollectionByNameOrId("_superusers"));
        admin.set("email", "admin@localhost.invalid");
        admin.setPassword(password);
        app.save(admin);
    }
    const settings = app.settings();
    settings.meta.appName = "Re-TUI community";
    settings.meta.appURL = "https://irc.kokodoko.cloud/community";
    settings.logs.maxDays = 0;
    settings.backups.cron = "37 3 * * *";
    settings.backups.cronMaxKeep = 7;
    settings.superuserIPs = ["127.0.0.1", "::1"];
    settings.trustedProxy.headers = ["X-Real-IP"];
    settings.rateLimits.enabled = true;
    settings.rateLimits.excludedIPs = ["127.0.0.1", "::1"];
    settings.rateLimits.rules = [
        {label: "POST /api/collections/members/records", audience: "", duration: 60, maxRequests: 5},
        {label: "POST /api/collections/members/auth-with-password", audience: "", duration: 60, maxRequests: 20},
        {label: "messages:create", audience: "", duration: 60, maxRequests: 20},
        {label: "reports:create", audience: "", duration: 60, maxRequests: 5},
        {label: "/api/", audience: "", duration: 60, maxRequests: 120},
    ];
    app.save(settings);
}, (app) => { app.delete(app.findCollectionByNameOrId("members")); });
