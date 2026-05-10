CREATE TABLE IF NOT EXISTS presets (
  id TEXT PRIMARY KEY,
  slug TEXT NOT NULL UNIQUE,
  name TEXT NOT NULL,
  description TEXT NOT NULL,
  tags TEXT NOT NULL DEFAULT '[]',
  app_version TEXT,
  status TEXT NOT NULL DEFAULT 'pending',
  screenshot_key TEXT NOT NULL,
  screenshot_type TEXT NOT NULL,
  screenshot_size INTEGER NOT NULL,
  preset_key TEXT NOT NULL,
  preset_type TEXT NOT NULL,
  preset_size INTEGER NOT NULL,
  wallpaper_key TEXT,
  wallpaper_type TEXT,
  wallpaper_size INTEGER,
  submitter_name TEXT,
  consent INTEGER NOT NULL DEFAULT 0,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  approved_at TEXT,
  reviewed_at TEXT,
  review_notes TEXT
);

CREATE INDEX IF NOT EXISTS idx_presets_status_created_at ON presets(status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_presets_slug ON presets(slug);
