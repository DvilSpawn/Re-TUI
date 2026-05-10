export async function onRequestGet({ env, request }) {
  const result = await env.RETUI_PRESETS.prepare(
    `SELECT id, slug, name, description, tags, app_version, status,
            screenshot_type, screenshot_size, preset_type, preset_size,
            wallpaper_type, wallpaper_size, created_at, approved_at
       FROM presets
      WHERE status = 'approved'
      ORDER BY COALESCE(approved_at, created_at) DESC`
  ).all();

  const presets = (result.results || []).map((preset) => ({
    id: preset.id,
    slug: preset.slug,
    name: preset.name,
    description: preset.description,
    tags: parseTags(preset.tags),
    appVersion: preset.app_version,
    status: preset.status,
    screenshotUrl: `/api/presets/assets/${preset.id}/screenshot`,
    downloadUrl: `/api/presets/assets/${preset.id}/preset`,
    wallpaperUrl: preset.wallpaper_type ? `/api/presets/assets/${preset.id}/wallpaper` : null,
    screenshotSize: preset.screenshot_size,
    presetSize: preset.preset_size,
    wallpaperSize: preset.wallpaper_size,
    createdAt: preset.created_at,
    approvedAt: preset.approved_at
  }));

  return json({ presets });
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
