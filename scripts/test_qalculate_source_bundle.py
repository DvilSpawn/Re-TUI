import hashlib
import io
import json
from pathlib import Path
import tarfile
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import qalculate_source_bundle as bundle


class SourceBundleTest(unittest.TestCase):
    def test_local_release_context_is_never_public_source(self):
        for name in bundle.LOCAL_ONLY_FILES | {'server/irc/operations.py', 'server/private-report.json'}:
            self.assertFalse(bundle.public_source_path(name), name)
        for name in ['app/src/main/AndroidManifest.xml', 'app/build.gradle',
                     'LICENSES/MIT-original.txt', 'scripts/language_packs.py',
                     'app/src/test/java/example/Test.kt', 'native/qalculate/build.py']:
            self.assertTrue(bundle.public_source_path(name), name)

    def test_pinned_source_reuses_bundle_and_rejects_changed_cache(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            source = root / 'bundled.tar.gz'
            source.write_bytes(b'known source')
            item = {'archive': 'source.tar.gz', 'sha256': hashlib.sha256(source.read_bytes()).hexdigest(),
                    'url': 'https://example.invalid/source'}
            with patch('qalculate_source_bundle.urllib.request.urlopen') as download:
                cached = bundle.fetch_pinned_source(item, root / 'downloads', source)
                self.assertEqual(source.read_bytes(), cached.read_bytes())
                download.assert_not_called()
                cached.write_bytes(b'wrong source')
                with self.assertRaisesRegex(ValueError, 'checksum mismatch'):
                    bundle.fetch_pinned_source(item, root / 'downloads', source)

    def test_bad_download_does_not_leave_a_cache_or_partial_file(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            item = {'archive': 'source.tar.gz', 'sha256': '0' * 64, 'url': 'https://example.invalid/source'}
            with patch('qalculate_source_bundle.urllib.request.urlopen', return_value=io.BytesIO(b'wrong')):
                with self.assertRaisesRegex(ValueError, 'checksum mismatch'):
                    bundle.fetch_pinned_source(item, root / 'downloads', root / 'missing')
            self.assertEqual([], list((root / 'downloads').iterdir()))

    def test_source_comparison_requires_build_files_and_matching_code(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            repository, published = root / 'repo.tar.gz', root / 'sources.jar'
            prefix = 'TaskerPluginSample-test/'
            required = ['LICENSE', 'build.gradle', 'settings.gradle',
                        'gradle/wrapper/gradle-wrapper.jar', 'gradle/wrapper/gradle-wrapper.properties',
                        'scripts/publish-mavencentral.gradle', 'taskerpluginlibrary/build.gradle',
                        'taskerpluginlibrary/proguard-rules.pro', 'taskerpluginlibrary/src/main/AndroidManifest.xml']

            def archive(include_build=True):
                with tarfile.open(repository, 'w:gz') as output:
                    files = {prefix + 'taskerpluginlibrary/src/main/java/example/Plugin.kt': b'class Plugin\r\n'}
                    if include_build:
                        files.update({prefix + name: b'build material' for name in required})
                    for name, data in files.items():
                        member = tarfile.TarInfo(name)
                        member.size = len(data)
                        output.addfile(member, io.BytesIO(data))

            def sources(data):
                with zipfile.ZipFile(published, 'w') as output:
                    output.writestr('example/Plugin.kt', data)

            archive(); sources(b'class Plugin\n')
            self.assertEqual(1, bundle.verify_tasker_sources(repository, published, 'test')['matched_source_files'])
            sources(b'class Different\n')
            with self.assertRaisesRegex(ValueError, 'does not match'):
                bundle.verify_tasker_sources(repository, published, 'test')
            archive(include_build=False)
            with self.assertRaisesRegex(ValueError, 'lacks license/build'):
                bundle.verify_tasker_sources(repository, published, 'test')

    def test_runtime_manifest_has_texts_and_tasker_source_pin(self):
        entries = json.loads((bundle.ROOT / 'third_party/runtime-libraries.json').read_text())
        coordinates = [entry['coordinate'] for entry in entries]
        self.assertEqual(len(coordinates), len(set(coordinates)))
        for entry in entries:
            self.assertTrue(entry['license_files'])
            for name in entry['license_files']:
                self.assertTrue((bundle.ROOT / 'LICENSES' / name).is_file(), name)
        sources = json.loads((bundle.ROOT / 'third_party/sources.json').read_text())
        self.assertEqual(['com.joaomgcd:taskerpluginlibrary:0.4.10'], [item['coordinate'] for item in sources])
        self.assertIn(sources[0]['coordinate'], coordinates)
        for item in [sources[0], sources[0]['published_sources']]:
            self.assertRegex(item['sha256'], r'^[0-9a-f]{64}$')


if __name__ == '__main__':
    unittest.main()
