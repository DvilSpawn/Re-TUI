#!/usr/bin/env python3
"""Validate the actual packager output and APK artwork boundary when an APK is supplied."""
import hashlib
import json
import os
from pathlib import Path
import struct
import tempfile
import unittest
import zipfile
from package_wallpaper_gallery import prepare

ROOT = Path(__file__).resolve().parents[1]


class WallpaperGalleryTest(unittest.TestCase):
    def test_real_artifacts_match_catalogue_and_installer_limits(self):
        with tempfile.TemporaryDirectory() as folder:
            output = Path(folder)
            prepare(output)
            catalog = json.loads((output / 'catalog.json').read_text())
            self.assertEqual(['topo', 'pixel', 'clouds', 'nagomi'], [row['id'] for row in catalog['wallpapers']])
            page = (output / 'index.html').read_text()
            self.assertNotIn('{{', page)
            for row in catalog['wallpapers']:
                archive = output / row['download']
                self.assertEqual(row['bytes'], archive.stat().st_size)
                self.assertEqual(row['sha256'], hashlib.sha256(archive.read_bytes()).hexdigest())
                self.assertLessEqual(row['bytes'], 4 * 1024 * 1024)
                self.assertTrue((output / row['preview']).read_bytes().startswith((b'GIF87a', b'GIF89a')))
                self.assertIn(f'retui-wallpaper://use/{row["id"]}', page)
                with zipfile.ZipFile(archive) as pack:
                    assets = {path.name for path in (ROOT / 'wallpaper-packs/content' / row['id']).glob('*.png')}
                    self.assertEqual(assets | {'wallpaper.json', 'LICENSE.txt'}, set(pack.namelist()))
                    self.assertEqual(row['id'], json.loads(pack.read('wallpaper.json'))['id'])
                    self.assertLessEqual(sum(item.file_size for item in pack.infolist()), 4 * 1024 * 1024)
                    self.assertLessEqual(pack.getinfo('LICENSE.txt').file_size, 64 * 1024)
                    for name in assets:
                        image = pack.read(name)
                        self.assertEqual(image, (ROOT / 'wallpaper-packs/content' / row['id'] / name).read_bytes())
                        self.assertEqual(b'\x89PNG\r\n\x1a\n', image[:8])
                        width, height = struct.unpack('>II', image[16:24])
                        self.assertTrue(1 <= width <= 1024 and 1 <= height <= 2048)

    @unittest.skipUnless(os.environ.get('RETUI_WALLPAPER_APK'), 'Set RETUI_WALLPAPER_APK to verify a freshly built APK')
    def test_apk_excludes_optional_artwork_and_preview_gifs(self):
        with zipfile.ZipFile(os.environ['RETUI_WALLPAPER_APK']) as apk:
            self.assertFalse(any('clouds_' in name or 'nagomi_pond_bed' in name or name.endswith('.gif') for name in apk.namelist()))


if __name__ == '__main__':
    unittest.main()
