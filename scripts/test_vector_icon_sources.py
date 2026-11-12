import unittest
import hashlib
import json
import xml.etree.ElementTree as ET
import vector_icon_sources as icons


class VectorSourcesTest(unittest.TestCase):
    def test_legacy_svg_without_viewbox_keeps_pixel_coordinates(self):
        svg = b'<svg xmlns="http://www.w3.org/2000/svg" width="24px" height="24px"><path d="M1 2h3Z"/></svg>'
        root = ET.fromstring(icons.convert(svg, "24dp", "24dp"))
        self.assertEqual("24", root.attrib[icons.ANDROID + "viewportWidth"])
        self.assertEqual(0.0, float(root[0].attrib[icons.ANDROID + "translateY"]))
        with self.assertRaises(AssertionError):
            icons.convert(svg.replace(b"24px", b"100%"), "24dp", "24dp")

    def test_exact_paths_and_origin_translation_are_preserved(self):
        svg = b'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 -960 960 960"><path d="M0-960h20v20Z"/></svg>'
        root = ET.fromstring(icons.convert(svg, "40dp", "40dp"))
        self.assertEqual("40dp", root.attrib[icons.ANDROID + "width"])
        self.assertEqual("960.0", root[0].attrib[icons.ANDROID + "translateY"])
        self.assertEqual("M0-960h20v20Z", root[0][0].attrib[icons.ANDROID + "pathData"])

    def test_no_guessed_transform_or_brand_logo(self):
        for svg in (b'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><g/></svg>',
                    b'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path d="M0 0" transform="translate(1)"/></svg>'):
            with self.assertRaises(AssertionError):
                icons.convert(svg, "24dp", "24dp")
        self.assertNotIn("ic_tuixt_github_24", icons.OUTLINED)
        self.assertNotIn("ic_tuixt_discord_24", icons.OUTLINED)
        self.assertNotIn("ic_tuixt_reddit_24", icons.OUTLINED)

    def test_current_vectors_all_have_source_and_unchanged_reviewed_hashes(self):
        material = json.loads((icons.ROOT / "third_party/material-icons.json").read_text())
        own = json.loads((icons.ROOT / "third_party/project-vector-icons.json").read_text())
        self.assertEqual(63, len(material["files"]))
        brand = json.loads((icons.ROOT / "third_party/brand-icons.json").read_text())
        known = set()
        for entry in material["files"] + own["files"] + brand["files"]:
            path = icons.ROOT / entry["path"]
            known.add(path)
            self.assertEqual(entry.get("vector_sha256", entry.get("sha256")), hashlib.sha256(path.read_bytes()).hexdigest())
        for path in (icons.ROOT / "app/src/main/res/drawable").glob("remember_ic_tree_*.xml"):
            if "Tabler" in path.read_text():
                known.add(path)
        vectors = {p for p in (icons.ROOT / "app/src/main/res/drawable").glob("*.xml")
                   if any(item.tag == "vector" for item in ET.parse(p).getroot().iter())}
        self.assertEqual({p for p in known if p.suffix == ".xml"}, vectors)
        self.assertFalse(list((icons.ROOT / "app/src/main/res/drawable").glob("remember_ic_format_*.png")))


if __name__ == "__main__":
    unittest.main()
