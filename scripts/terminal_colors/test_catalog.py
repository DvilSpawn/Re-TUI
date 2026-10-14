import copy
import json
import random
import tempfile
import unittest
import zipfile
from pathlib import Path

from generate import HERE, ANSI, contrast, map_palette, package, readable, validate_registry, featured_ids


class CatalogTest(unittest.TestCase):
    def test_all_source_palettes_and_role_coverage(self):
        registry = json.loads((HERE / "coverage.json").read_text())
        validate_registry(registry)
        count = 0
        for slug in featured_ids():
            path = HERE / 'source' / (slug + '.json')
            record = json.loads(path.read_text())
            theme, suggestions, changes, checks = map_palette(record['palette'], registry)
            self.assertEqual(set(theme), set(registry['roles']))
            self.assertEqual(len(suggestions), 14)
            self.assertTrue(all(check['ratio'] >= check['threshold'] for check in checks), path.name)
            self.assertEqual(len([key for key in theme if key.startswith('ansi_')]), 16)
            self.assertEqual(record['palette']['background'].lower(), theme['background_color'])
            count += 1
        self.assertEqual(count, 21)
        self.assertEqual(set(featured_ids()), set('catppuccin-frappe catppuccin-latte catppuccin-macchiato catppuccin-mocha claude-dark claude-light cobalt2 dracula dracula-plus github-dark gruvbox-dark gruvbox-light material-dark night-owl nord rose-pine rose-pine-dawn tokyonight tokyonight-day tokyonight-storm tomorrow'.split()))

    def test_missing_or_new_roles_fail_closed(self):
        registry = json.loads((HERE / 'coverage.json').read_text())
        del registry['roles']['input_text_color']
        with self.assertRaisesRegex(ValueError, 'drift'):
            validate_registry(registry)

    def test_unsafe_or_incomplete_source_rejected(self):
        source = json.loads((HERE / 'source/catppuccin-mocha.json').read_text())['palette']
        registry = json.loads((HERE / 'coverage.json').read_text())
        for key in ANSI + ['foreground', 'background', 'cursorColor', 'selectionBackground']:
            bad = copy.deepcopy(source); bad[key] = '#00000000'
            with self.assertRaises(ValueError):
                map_palette(bad, registry)
        bad = copy.deepcopy(source); del bad['brightRed']
        with self.assertRaises(ValueError):
            map_palette(bad, registry)

    def test_preserves_passing_colors_and_unrounded_boundary(self):
        self.assertEqual(readable('#000000', ['#ffffff']), '#000000')
        self.assertNotEqual(readable('#777777', ['#ffffff']), '#777777')
        self.assertGreaterEqual(contrast(readable('#777777', ['#ffffff']), '#ffffff'), 4.5)
        rng = random.Random(614)
        for _ in range(100):
            first, second = [f'#{rng.randrange(1 << 24):06x}' for _ in range(2)]
            adjusted = readable(first, [second])
            self.assertGreaterEqual(contrast(adjusted, second), 4.5)
            self.assertEqual(readable(adjusted, [second]), adjusted)

    def test_packages_reproducible_and_color_only(self):
        with tempfile.TemporaryDirectory() as folder:
            a, b = [Path(folder) / n for n in ('a.retui', 'b.retui')]
            for path in (a, b):
                package(path, 'Palette\nname', {'input_text_color': '#ffffff'}, {'default_text_color': '#ffffff'}, '3')
            self.assertEqual(a.read_bytes(), b.read_bytes())
            with zipfile.ZipFile(a) as archive:
                self.assertEqual(set(archive.namelist()), {'manifest.txt', 'theme.xml', 'suggestions.xml'})
                self.assertIsNone(archive.testzip())
                manifest = archive.read('manifest.txt').decode()
                self.assertIn('schema=2\n', manifest)
                self.assertIn('sections=theme,suggestions\n', manifest)
                self.assertIn('presetName=Palette name\n', manifest)

    def test_smallest_adjustment_includes_rgb_values_between_fixed_steps(self):
        self.assertEqual(readable('#27bdc3', ['#5b4879']), '#6dd3d7')
        self.assertGreaterEqual(contrast('#6dd3d7', '#5b4879'), 4.5)
        self.assertLess(contrast('#6dd2d7', '#5b4879'), 4.5)


if __name__ == '__main__':
    unittest.main()
