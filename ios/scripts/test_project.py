"""Static packaging contracts; these do not replace an Xcode build."""
import json
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

    def test_native_tests_are_required(self):
        tests = self.project['schemes']['NoScrollIOS']['test']['targets']
        self.assertIn('NoScrollNativeTests', tests)
        self.assertEqual(self.project['options']['deploymentTarget']['iOS'], '17.0')
        self.assertTrue((ROOT / 'scripts/verify-macos.sh').is_file())

if __name__ == '__main__':
    unittest.main()
