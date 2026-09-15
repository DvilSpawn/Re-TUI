import unittest
from pathlib import Path
import xml.etree.ElementTree as ET
from localization_audit import placeholders, validate_translation


def entries(xml):
    return {n.attrib['name']: (Path('strings.xml'), n) for n in ET.fromstring(xml)}


class LocalizationChecks(unittest.TestCase):
    def test_placeholder_reordering_and_types(self):
        self.assertEqual(placeholders('%1$s: %2$d %%'), placeholders('%2$d — %1$s %%'))
        self.assertNotEqual(placeholders('%1$s'), placeholders('%1$d'))
        self.assertEqual(placeholders('%1$s %<s'), placeholders('%1$s %1$s'))

    def test_rejects_broken_translation_but_accepts_partial_file(self):
        base = entries('<resources><string name="count">%1$d items</string><string name="name" translatable="false">Re:T-UI</string></resources>')
        self.assertEqual([], validate_translation(base, {}))
        translated = entries('<resources><string name="count">%1$s items</string><string name="name">New name</string><string name="unknown">Extra</string></resources>')
        self.assertEqual(3, len(validate_translation(base, translated)))

    def test_locale_can_add_plural_categories_with_same_arguments(self):
        base = entries('<resources><plurals name="items"><item quantity="one">%1$d item</item><item quantity="other">%1$d items</item></plurals></resources>')
        translated = entries('<resources><plurals name="items"><item quantity="few">%1$d items</item><item quantity="other">%1$d items</item></plurals></resources>')
        self.assertEqual([], validate_translation(base, translated))


if __name__ == '__main__':
    unittest.main()
