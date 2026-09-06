"""Static packaging contracts; these do not replace an Xcode build."""
import json
import os
import shutil
import subprocess
import tempfile
import pathlib
import plistlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]

class ProjectTests(unittest.TestCase):
    def setUp(self):
        self.assertTrue((ROOT / 'project.json').is_file(), 'Xcode project specification is missing')
        self.project = json.loads((ROOT / 'project.json').read_text())

    def test_targets_and_embedding(self):
        targets = self.project['targets']
        extensions = {'NoScrollBroadcast', 'NoScrollShieldAction', 'NoScrollShieldConfiguration'}
        self.assertEqual(set(targets), extensions | {'NoScrollIOS', 'NoScrollNativeTests'})
        embedded = {d['target'] for d in targets['NoScrollIOS']['dependencies'] if d.get('embed')}
        self.assertEqual(embedded, extensions)
        for name, target in targets.items():
            self.assertEqual(target['platform'], 'iOS')
            for source in target['sources']:
                self.assertTrue((ROOT / source['path']).exists(), (name, source))

    def test_entitlements_and_privacy(self):
        for name, target in self.project['targets'].items():
            if name == 'NoScrollNativeTests':
                continue
            settings = target['settings']['base']
            entitlements = plistlib.loads((ROOT / settings['CODE_SIGN_ENTITLEMENTS']).read_bytes())
            self.assertEqual(entitlements['com.apple.security.application-groups'], ['$(NOSCROLL_APP_GROUP)'])
            self.assertTrue(entitlements['com.apple.developer.family-controls'])
            privacy = [s for s in target['sources'] if s['path'].endswith('.xcprivacy')]
            self.assertEqual(len(privacy), 1)
            self.assertEqual(privacy[0]['buildPhase'], 'resources')
            manifest = plistlib.loads((ROOT / privacy[0]['path']).read_bytes())
            self.assertFalse(manifest['NSPrivacyTracking'])
            self.assertEqual(manifest['NSPrivacyCollectedDataTypes'], [])

    def test_extension_entrypoints(self):
        broadcast = plistlib.loads((ROOT / 'Configuration/Broadcast-Info.plist').read_bytes())['NSExtension']
        self.assertEqual(broadcast['RPBroadcastProcessMode'], 'RPBroadcastProcessModeSampleBuffer')
        self.assertEqual(broadcast['NSExtensionPointIdentifier'], 'com.apple.broadcast-services-upload')
        self.assertTrue(broadcast['NSExtensionPrincipalClass'].endswith('.SampleHandler'))
        for name in ['App', 'Broadcast', 'ShieldAction', 'ShieldConfiguration']:
            info = plistlib.loads((ROOT / f'Configuration/{name}-Info.plist').read_bytes())
            self.assertEqual(info['NoScrollAppGroup'], '$(NOSCROLL_APP_GROUP)')
            self.assertNotIn('UIBackgroundModes', info)
            self.assertNotIn('NSCameraUsageDescription', info)
            self.assertNotIn('NSMicrophoneUsageDescription', info)

    def test_mac_verification_resolves_local_package_from_ios_directory(self):
        # Execute the real orchestration script from an unrelated caller directory,
        # with only the external Apple tools replaced by harmless recording shims.
        with tempfile.TemporaryDirectory() as temporary:
            base = pathlib.Path(temporary)
            tools = base / 'bin'
            tools.mkdir()
            log = base / 'calls.jsonl'
            python = shutil.which('python3')
            self.assertIsNotNone(python)
            shim = """#!PYTHON
import json, os, pathlib, sys
name = pathlib.Path(sys.argv[0]).name
args = sys.argv[1:]
with open(os.environ['NOSCROLL_TEST_LOG'], 'a') as stream:
    stream.write(json.dumps({'tool': name, 'args': args, 'cwd': os.getcwd()}) + '\\n')
if name == 'xcrun':
    print(json.dumps({'devices': {'com.apple.CoreSimulator.SimRuntime.iOS-26-2': [
        {'isAvailable': True, 'name': 'iPhone test', 'udid': 'TEST-DEVICE'}]}}))
elif name == 'python3' and args and args[0] == '-c':
    os.execv('PYTHON', ['PYTHON'] + args)
""".replace('PYTHON', python)
            for name in ['xcodebuild', 'xcodegen', 'swift', 'xcrun', 'python3']:
                executable = tools / name
                executable.write_text(shim)
                executable.chmod(0o755)
            environment = dict(os.environ, PATH=str(tools) + os.pathsep + os.environ['PATH'],
                               NOSCROLL_TEST_LOG=str(log))
            result = subprocess.run(['bash', str(ROOT / 'scripts/verify-macos.sh')],
                                    cwd=base, env=environment, text=True, capture_output=True, timeout=15)
            self.assertEqual(result.returncode, 0, result.stderr)
            calls = [json.loads(line) for line in log.read_text().splitlines()]
            actual = [call for call in calls if
                      (call['tool'] == 'xcodegen' and 'generate' in call['args']) or
                      (call['tool'] == 'xcodebuild' and 'test' in call['args'])]
            self.assertEqual(len(actual), 2)
            for call in actual:
                self.assertEqual(pathlib.Path(call['cwd']).resolve(), ROOT, call)
                package = ROOT / self.project['packages']['NoScrollCore']['path'] / 'Package.swift'
                self.assertTrue(package.is_file())

    def test_native_tests_are_required(self):
        tests = self.project['schemes']['NoScrollIOS']['test']['targets']
        self.assertIn('NoScrollNativeTests', tests)
        self.assertEqual(self.project['options']['deploymentTarget']['iOS'], '17.0')
        self.assertTrue((ROOT / 'scripts/verify-macos.sh').is_file())

if __name__ == '__main__':
    unittest.main()
