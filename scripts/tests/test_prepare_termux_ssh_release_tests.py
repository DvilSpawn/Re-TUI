import importlib.util
from pathlib import Path
import struct
import tempfile
import unittest
import zipfile

spec = importlib.util.spec_from_file_location('prepare_ssh_tests', Path(__file__).parents[1] / 'prepare_termux_ssh_release_tests.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


def dex(names):
    count = len(names)
    strings, types, classes = 112, 112 + count * 4, 112 + count * 8
    body = bytearray(classes + count * 32)
    body[:8] = b'dex\n035\0'
    struct.pack_into('<II', body, 0x38, count, strings)
    struct.pack_into('<II', body, 0x40, count, types)
    struct.pack_into('<II', body, 0x60, count, classes)
    for index, name in enumerate(names):
        encoded = name.encode()
        struct.pack_into('<I', body, strings + index * 4, len(body))
        struct.pack_into('<I', body, types + index * 4, index)
        struct.pack_into('<I', body, classes + index * 32, index)
        body.extend(bytes([len(encoded)]) + encoded + b'\0')
    return bytes(body)


class RuntimeIsolationTest(unittest.TestCase):
    def test_rejects_mixed_app_and_compatibility_dex(self):
        names = module.dex_descriptors(dex(['Lj$/util/stream/Test;', 'Lour/ActualTest;']))
        with self.assertRaisesRegex(ValueError, 'share a DEX'):
            module.compat_classes(names, set())

    def test_requires_app_to_provide_function_interfaces(self):
        names = module.dex_descriptors(dex(['Lj$/util/Test;', 'Ljava/util/function/Function;']))
        with self.assertRaisesRegex(ValueError, 'does not provide'):
            module.compat_classes(names, set())
        self.assertTrue(module.compat_classes(names, {'Ljava/util/function/Function;'}))

    def test_repack_preserves_all_program_code_resources_and_original(self):
        with tempfile.TemporaryDirectory() as folder:
            folder = Path(folder)
            app, tests, output = (folder / name for name in ('app.apk', 'tests.apk', 'output.apk'))
            with zipfile.ZipFile(app, 'w') as archive:
                archive.writestr('classes.dex', dex(['Ljava/util/function/Function;']))
            program = dex(['Lour/ActualTest;'])
            with zipfile.ZipFile(tests, 'w') as archive:
                archive.writestr('classes.dex', program)
                archive.writestr('classes2.dex', dex(['Lj$/util/Test;', 'Ljava/util/function/Function;']))
                archive.writestr('resources.arsc', b'unchanged resources')
                archive.writestr('META-INF/CERT.RSA', b'old signature')
            original = tests.read_bytes()
            removed = module.isolate_test_runtime(app, tests, output)
            self.assertEqual(2, removed[0]['class_count'])
            self.assertEqual(original, tests.read_bytes())
            with zipfile.ZipFile(output) as archive:
                self.assertEqual(program, archive.read('classes.dex'))
                self.assertEqual(b'unchanged resources', archive.read('resources.arsc'))
                self.assertNotIn('classes2.dex', archive.namelist())
                self.assertNotIn('META-INF/CERT.RSA', archive.namelist())


if __name__ == '__main__':
    unittest.main()
