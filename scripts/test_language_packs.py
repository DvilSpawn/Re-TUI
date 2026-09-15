import json
import tempfile
import unittest
from pathlib import Path
import language_packs as packs


class LanguagePacksTest(unittest.TestCase):
    def test_template_is_current(self):
        packs.export(check=True)

    def test_plain_xml_preserves_android_escapes_and_quoted_spaces(self):
        self.assertEqual(' hi\nthere "yes" ', packs.android_text('" hi\\nthere \\"yes\\" "'))
        self.assertEqual("Don't", packs.android_text("Don\\'t"))

    def test_builder_rejects_wrong_arguments_and_protected_keys(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            manifest = json.loads((packs.ROOT/'language-packs/template/manifest.json').read_text())
            (root/'manifest.json').write_text(json.dumps(manifest))
            for key, text in [('language_packs_active', '%1$d'), ('ui_token_48bb4ccc18', 'Changed')]:
                (root/'strings.xml').write_text(f'<resources><string name="{key}">{text}</string></resources>')
                with self.assertRaises(AssertionError): packs.build(root, root/'out.retui-launcher-lang')
                self.assertFalse((root/'out.retui-launcher-lang').exists())
            (root/'strings.xml').write_text('<resources><string name="language_packs_active">Active: %1$s</string></resources>')
            packs.build(root, root/'out.retui-launcher-lang')
            self.assertTrue((root/'out.retui-launcher-lang').is_file())


if __name__ == '__main__': unittest.main()
