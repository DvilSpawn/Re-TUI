export async function onRequestGet({ env, request, params }) {
  const authResponse = authorize(request, env);
  if (authResponse) return authResponse;

  const id = clean(params.id);
  const kind = clean(params.kind);
  if (!id || !["screenshot", "preset", "wallpaper"].includes(kind)) {
    return new Response("Not found", { status: 404 });
  }

  const row = await env.RETUI_PRESETS.prepare(
    `SELECT slug,
            screenshot_key, screenshot_type,
            preset_key, preset_type,
            wallpaper_key, wallpaper_type
       FROM presets
      WHERE id = ?`
  ).bind(id).first();

  if (!row) return new Response("Not found", { status: 404 });

  const key = row[`${kind}_key`];
  const type = row[`${kind}_type`];
  if (!key || !type) return new Response("Not found", { status: 404 });

  const object = await env.RETUI_PRESET_ASSETS.get(key);
  if (!object) return new Response("Not found", { status: 404 });

  const headers = new Headers({
    "content-type": type,
    "cache-control": "no-store"
  });
  if (kind === "preset") {
    headers.set("content-disposition", `attachment; filename="${row.slug || id}.retui-backup.zip"`);
  }

  return new Response(object.body, { headers });
}

function authorize(request, env) {
  const expected = env.RETUI_ADMIN_TOKEN;
  if (!expected) return json({ error: "Admin token is not configured." }, 503);

  const header = request.headers.get("authorization") || "";
  const bearer = header.replace(/^Bearer\s+/i, "");
  const token = bearer || request.headers.get("x-retui-admin-token") || "";
  if (token !== expected) return json({ error: "Unauthorized." }, 401);
  return null;
}

function clean(value) {
  const text = String(value || "");
  return /^[a-z0-9-]+$/.test(text) ? text : "";
}

function json(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store"
    }
  });
}
