export async function onRequestGet({ env, params }) {
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
      WHERE id = ? AND status = 'approved'`
  ).bind(id).first();

  if (!row) return new Response("Not found", { status: 404 });

  const key = row[`${kind}_key`];
  const type = row[`${kind}_type`];
  if (!key || !type) return new Response("Not found", { status: 404 });

  const object = await env.RETUI_PRESET_ASSETS.get(key);
  if (!object) return new Response("Not found", { status: 404 });

  const headers = new Headers({
    "content-type": type,
    "cache-control": "public, max-age=86400"
  });
  if (kind === "preset") {
    headers.set("content-disposition", `attachment; filename="${row.slug || id}.retui-backup.zip"`);
  }

  return new Response(object.body, { headers });
}

function clean(value) {
  const text = String(value || "");
  return /^[a-z0-9-]+$/.test(text) ? text : "";
}
