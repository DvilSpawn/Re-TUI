const STATUSES = new Set(["pending", "approved", "rejected"]);

export async function onRequestGet({ env, request }) {
  const authResponse = authorize(request, env);
  if (authResponse) return authResponse;

  const url = new URL(request.url);
  const status = url.searchParams.get("status") || "pending";
  if (!STATUSES.has(status)) return json({ error: "Unknown status." }, 400);

  const result = await env.RETUI_PRESETS.prepare(
    `SELECT id, slug, name, description, tags, app_version, status,
            screenshot_size, preset_size, wallpaper_size,
            submitter_name, created_at, approved_at, reviewed_at, review_notes
       FROM presets
      WHERE status = ?
      ORDER BY created_at DESC`
  ).bind(status).all();

  return json({
    presets: (result.results || []).map((preset) => ({
      id: preset.id,
      slug: preset.slug,
      name: preset.name,
      description: preset.description,
      tags: parseTags(preset.tags),
      appVersion: preset.app_version,
      status: preset.status,
      screenshotSize: preset.screenshot_size,
      presetSize: preset.preset_size,
      wallpaperSize: preset.wallpaper_size,
      submitterName: preset.submitter_name,
      createdAt: preset.created_at,
      approvedAt: preset.approved_at,
      reviewedAt: preset.reviewed_at,
      reviewNotes: preset.review_notes,
      hasWallpaper: Boolean(preset.wallpaper_size)
    }))
  });
}

export async function onRequestPost({ env, request }) {
  const authResponse = authorize(request, env);
  if (authResponse) return authResponse;

  let body;
  try {
    body = await request.json();
  } catch (error) {
    return json({ error: "Send JSON." }, 400);
  }

  const id = cleanId(body.id);
  const action = String(body.action || "").toLowerCase();
  const notes = cleanText(body.notes, 500);
  if (!id) return json({ error: "Preset id is required." }, 400);
  if (!["approve", "reject"].includes(action)) return json({ error: "Action must be approve or reject." }, 400);

  const nextStatus = action === "approve" ? "approved" : "rejected";
  const result = await env.RETUI_PRESETS.prepare(
    `UPDATE presets
        SET status = ?,
            approved_at = CASE WHEN ? = 'approved' THEN CURRENT_TIMESTAMP ELSE approved_at END,
            reviewed_at = CURRENT_TIMESTAMP,
            review_notes = ?
      WHERE id = ?`
  ).bind(nextStatus, nextStatus, notes || null, id).run();

  if (!result.meta || result.meta.changes === 0) return json({ error: "Preset not found." }, 404);
  return json({ ok: true, id, status: nextStatus });
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

function cleanId(value) {
  const text = String(value || "");
  return /^[a-z0-9-]+$/.test(text) ? text : "";
}

function cleanText(value, maxLength) {
  return String(value || "").replace(/\s+/g, " ").trim().slice(0, maxLength);
}

function parseTags(value) {
  try {
    const tags = JSON.parse(value || "[]");
    return Array.isArray(tags) ? tags : [];
  } catch (error) {
    return [];
  }
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
