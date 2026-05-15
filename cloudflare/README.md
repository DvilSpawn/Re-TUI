# Re:T-UI Preset Marketplace

The website uses Cloudflare Pages Functions for preset uploads and browsing.

- D1 stores preset metadata and moderation status.
- R2 stores screenshots, preset packages, and optional wallpaper images.
- Public gallery reads only approved presets from `/api/presets`.
- Public asset downloads only work for approved presets.
- Uploads land in `pending` status through `/api/presets/submit`.
- Staff moderation is available at `/moderate.html` after setting `RETUI_ADMIN_TOKEN` as a Pages secret.
- Experimental ASCII rendering is client-side at `/ascii.html`.

## Initial Setup

```sh
wrangler d1 execute retui-presets --remote --file cloudflare/d1/0001_preset_marketplace.sql
wrangler r2 bucket create retui-preset-assets --location apac
wrangler r2 object put retui-preset-assets/presets/northern-violet/screenshot --file docs/preset-assets/northern-violet/screenshot.png --content-type image/png --remote
wrangler r2 object put retui-preset-assets/presets/northern-violet/preset --file docs/preset-assets/northern-violet/northern-violet.retui-backup.zip --content-type application/zip --content-disposition 'attachment; filename="northern-violet.retui-backup.zip"' --remote
wrangler d1 execute retui-presets --remote --file cloudflare/d1/seed_northern_violet.sql
```

## Moderation Secret

Set a private token before using `/moderate.html`:

```sh
wrangler pages secret put RETUI_ADMIN_TOKEN --project-name re-tui
```

R2 is the production asset store. D1 remains the source of truth for moderation and public listing status.

## Experimental ASCII Renderer

The static UI lives at `/ascii.html`. Users can choose a wallpaper image, and the page renders it into ASCII locally with an offscreen canvas. The image is not uploaded to Cloudflare, there is no Workers AI dependency, and exports are generated in the browser as text, PNG, or SVG.
