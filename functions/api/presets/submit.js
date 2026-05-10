const MAX_SCREENSHOT_BYTES = 8 * 1024 * 1024;
const MAX_PRESET_BYTES = 5 * 1024 * 1024;
const MAX_WALLPAPER_BYTES = 8 * 1024 * 1024;

export async function onRequestPost({ env, request }) {
  try {
    return await handlePost({ env, request });
  } catch (error) {
    if (error instanceof SubmissionError) {
      return json({ error: error.message }, 400);
    }
    console.error(error);
    return json({ error: "Upload failed. Try again later." }, 500);
  }
}

async function handlePost({ env, request }) {
  const contentType = request.headers.get("content-type") || "";
  if (!contentType.includes("multipart/form-data")) {
    return json({ error: "Use multipart form data." }, 415);
  }

  const form = await request.formData();
  const name = cleanText(form.get("name"), 64);
  const description = cleanText(form.get("description"), 400);
  const tags = cleanTags(form.get("tags"));
  const submitterName = cleanText(form.get("submitterName"), 80);
  const website = cleanText(form.get("website"), 120);
  const consent = form.get("consent") === "on" || form.get("consent") === "true";
  const screenshot = form.get("screenshot");
  const preset = form.get("preset");
  const wallpaper = form.get("wallpaper");

  if (!name) return json({ error: "Preset name is required." }, 400);
  if (!description) return json({ error: "Description is required." }, 400);
  if (website) return json({ error: "Upload rejected." }, 400);
  if (!consent) return json({ error: "Safety confirmation is required." }, 400);
  if (!isFile(screenshot)) return json({ error: "Screenshot is required." }, 400);
  if (!isFile(preset)) return json({ error: "Preset file is required." }, 400);

  validateFile(screenshot, ["image/png", "image/jpeg", "image/webp"], MAX_SCREENSHOT_BYTES, "Screenshot");
  validateFile(preset, ["application/zip", "application/x-zip-compressed", "application/octet-stream"], MAX_PRESET_BYTES, "Preset");
  if (isFile(wallpaper)) {
    validateFile(wallpaper, ["image/png", "image/jpeg", "image/webp"], MAX_WALLPAPER_BYTES, "Wallpaper");
  }

  const id = crypto.randomUUID();
  const slug = await uniqueSlug(env, slugify(name));
  const presetBytes = await preset.arrayBuffer();
  const screenshotBytes = await screenshot.arrayBuffer();
  const wallpaperBytes = isFile(wallpaper) ? await wallpaper.arrayBuffer() : null;
  const appVersion = await readAppVersion(presetBytes);
  const screenshotType = normalizeType(screenshot.type, "image/png");
  const presetType = "application/zip";
  const wallpaperType = wallpaperBytes ? normalizeType(wallpaper.type, "image/png") : null;

  const screenshotKey = `presets/${id}/screenshot`;
  const presetKey = `presets/${id}/preset`;
  const wallpaperKey = wallpaperBytes ? `presets/${id}/wallpaper` : null;

  await env.RETUI_PRESET_ASSETS.put(screenshotKey, screenshotBytes, {
    httpMetadata: { contentType: screenshotType }
  });
  await env.RETUI_PRESET_ASSETS.put(presetKey, presetBytes, {
    httpMetadata: {
      contentType: presetType,
      contentDisposition: `attachment; filename="${slug}.retui-backup.zip"`
    }
  });
  if (wallpaperKey && wallpaperBytes) {
    await env.RETUI_PRESET_ASSETS.put(wallpaperKey, wallpaperBytes, {
      httpMetadata: { contentType: wallpaperType }
    });
  }

  await env.RETUI_PRESETS.prepare(
    `INSERT INTO presets (
      id, slug, name, description, tags, app_version, status,
      screenshot_key, screenshot_type, screenshot_size,
      preset_key, preset_type, preset_size,
      wallpaper_key, wallpaper_type, wallpaper_size,
      submitter_name, consent
    ) VALUES (?, ?, ?, ?, ?, ?, 'pending', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
  ).bind(
    id,
    slug,
    name,
    description,
    JSON.stringify(tags),
    appVersion,
    screenshotKey,
    screenshotType,
    screenshot.size,
    presetKey,
    presetType,
    preset.size,
    wallpaperKey,
    wallpaperType,
    wallpaperBytes ? wallpaper.size : null,
    submitterName || null,
    consent ? 1 : 0
  ).run();

  return json({
    ok: true,
    id,
    slug,
    status: "pending",
    appVersion,
    message: "Preset submitted for review."
  });
}

export async function onRequestOptions() {
  return new Response(null, { headers: corsHeaders() });
}

function isFile(value) {
  return value && typeof value === "object" && typeof value.arrayBuffer === "function" && value.size > 0;
}

function validateFile(file, allowedTypes, maxBytes, label) {
  if (file.size > maxBytes) {
    throw new SubmissionError(`${label} is too large.`);
  }
  const type = normalizeType(file.type, "");
  if (type && !allowedTypes.includes(type)) {
    throw new SubmissionError(`${label} file type is not supported.`);
  }
}

class SubmissionError extends Error {}

function cleanText(value, maxLength) {
  return String(value || "").replace(/\s+/g, " ").trim().slice(0, maxLength);
}

function cleanTags(value) {
  return String(value || "")
    .split(",")
    .map((tag) => tag.trim().toLowerCase())
    .filter(Boolean)
    .slice(0, 8);
}

function slugify(value) {
  const slug = String(value || "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 48);
  return slug || "preset";
}

async function uniqueSlug(env, base) {
  let slug = base;
  let index = 2;
  while (await env.RETUI_PRESETS.prepare("SELECT 1 FROM presets WHERE slug = ?").bind(slug).first()) {
    slug = `${base}-${index}`;
    index += 1;
  }
  return slug;
}

function normalizeType(value, fallback) {
  const type = String(value || "").toLowerCase();
  return type || fallback;
}

async function readAppVersion(buffer) {
  try {
    const entries = await readZipEntries(buffer, new Set(["manifest.txt", "manifest.json"]));
    const manifestText = entries.get("manifest.json") || entries.get("manifest.txt") || "";
    const manifest = parseManifest(manifestText);
    return manifest.appVersion || manifest.app_version || null;
  } catch (error) {
    return null;
  }
}

function parseManifest(text) {
  try {
    return JSON.parse(text);
  } catch (error) {
    return String(text || "").split(/\r?\n/).reduce((values, line) => {
      const index = line.indexOf("=");
      if (index > 0) values[line.slice(0, index)] = line.slice(index + 1);
      return values;
    }, {});
  }
}

async function readZipEntries(buffer, wanted) {
  const view = new DataView(buffer);
  const bytes = new Uint8Array(buffer);
  const decoder = new TextDecoder();
  const eocd = findEndOfCentralDirectory(view);
  const totalEntries = view.getUint16(eocd + 10, true);
  let offset = view.getUint32(eocd + 16, true);
  const entries = new Map();

  for (let index = 0; index < totalEntries; index += 1) {
    if (view.getUint32(offset, true) !== 0x02014b50) break;
    const method = view.getUint16(offset + 10, true);
    const compressedSize = view.getUint32(offset + 20, true);
    const nameLength = view.getUint16(offset + 28, true);
    const extraLength = view.getUint16(offset + 30, true);
    const commentLength = view.getUint16(offset + 32, true);
    const localOffset = view.getUint32(offset + 42, true);
    const name = decoder.decode(bytes.slice(offset + 46, offset + 46 + nameLength));

    if (wanted.has(name)) {
      const localNameLength = view.getUint16(localOffset + 26, true);
      const localExtraLength = view.getUint16(localOffset + 28, true);
      const dataStart = localOffset + 30 + localNameLength + localExtraLength;
      const compressed = bytes.slice(dataStart, dataStart + compressedSize);
      const data = method === 0 ? compressed : await inflate(compressed);
      entries.set(name, decoder.decode(data));
    }

    offset += 46 + nameLength + extraLength + commentLength;
  }

  return entries;
}

function findEndOfCentralDirectory(view) {
  for (let offset = view.byteLength - 22; offset >= 0; offset -= 1) {
    if (view.getUint32(offset, true) === 0x06054b50) return offset;
  }
  throw new Error("Missing ZIP directory.");
}

async function inflate(bytes) {
  const stream = new Blob([bytes]).stream().pipeThrough(new DecompressionStream("deflate-raw"));
  return new Uint8Array(await new Response(stream).arrayBuffer());
}

function json(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      ...corsHeaders(),
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store"
    }
  });
}

function corsHeaders() {
  return {
    "access-control-allow-origin": "*",
    "access-control-allow-methods": "POST, OPTIONS",
    "access-control-allow-headers": "content-type"
  };
}
