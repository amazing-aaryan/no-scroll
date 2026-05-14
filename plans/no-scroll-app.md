# no-scroll — Android PDF Reader with Instagram Overlay

**Objective:** Android app that overlays a book icon on top of Instagram's Reels tab button. Tapping it opens a fullscreen PDF reader. PDF selection is persisted — pick once, auto-loads forever. Floating button lets user swap PDF while reading.

**Stack:** React Native (TypeScript) + Native Kotlin modules  
**Target:** Android 8.0+ (API 26+)  
**Mode:** Direct (no git remote required)

---

## Architecture Overview

```
┌─────────────────────────────────────────────────┐
│  NoScrollAccessibilityService (Kotlin)          │
│  - Detects Instagram foreground                 │
│  - Reads Reels button bounds from a11y tree     │
│  - Fires event to OverlayService                │
└────────────────┬────────────────────────────────┘
                 │
┌────────────────▼────────────────────────────────┐
│  OverlayService (Kotlin)                        │
│  - SYSTEM_ALERT_WINDOW overlay                  │
│  - Draws book icon at exact Reels button coords │
│  - On tap -> starts PdfViewerActivity (RN)      │
└────────────────┬────────────────────────────────┘
                 │
┌────────────────▼────────────────────────────────┐
│  PdfViewerActivity (React Native)               │
│  - react-native-pdf for rendering               │
│  - AsyncStorage for persisted PDF URI           │
│  - FAB (floating action button) -> file picker  │
│  - react-native-document-picker for selection   │
└─────────────────────────────────────────────────┘
```

---

## Dependency Map

```
Step 1 --> Step 2 --> Step 3
                |
                v
           Step 4 --> Step 5 --> Step 7 --> Step 8
                |
           Step 6 <--+
```

Steps 3 and 4 are independent after Step 1+2 complete (can run in parallel).

---

## Step 1 — Project Scaffold

**Model:** default  
**Reversible:** yes

### Context
Greenfield project. Empty directory. Init React Native TypeScript project.

### Tasks
- [ ] Init React Native project:
  ```bash
  npx react-native@latest init NoScroll --template react-native-template-typescript
  ```
- [ ] Install core dependencies:
  ```bash
  npm install react-native-pdf react-native-document-picker @react-native-async-storage/async-storage react-native-blob-util
  ```
- [ ] Verify `android/` folder exists with standard RN structure
- [ ] Create `src/` directory structure: `screens/`, `components/`, `storage/`, `native/`, `hooks/`

### Verification
```bash
npx react-native doctor
cd android && ./gradlew tasks --quiet
npx tsc --noEmit
```

### Exit Criteria
- `android/` exists with `app/src/main/`
- TypeScript compiles clean
- `./gradlew assembleDebug` succeeds on a baseline build

---

## Step 2 — Android Permissions & Manifest

**Model:** default  
**Reversible:** yes

### Context
Declare all permissions and services before writing native Kotlin modules.

### Tasks
- [ ] Edit `android/app/src/main/AndroidManifest.xml`:
  - Add permissions:
    ```xml
    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW"/>
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"/>
    ```
  - Register AccessibilityService:
    ```xml
    <service
        android:name=".NoScrollAccessibilityService"
        android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
        android:exported="true">
        <intent-filter>
            <action android:name="android.accessibilityservice.AccessibilityService"/>
        </intent-filter>
        <meta-data
            android:name="android.accessibilityservice"
            android:resource="@xml/accessibility_service_config"/>
    </service>
    ```
  - Register OverlayService:
    ```xml
    <service android:name=".OverlayService" android:exported="false"/>
    ```
- [ ] Create `android/app/src/main/res/xml/accessibility_service_config.xml`:
  ```xml
  <?xml version="1.0" encoding="utf-8"?>
  <accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
      android:accessibilityEventTypes="typeWindowStateChanged|typeWindowContentChanged"
      android:accessibilityFeedbackType="feedbackGeneric"
      android:accessibilityFlags="flagDefault"
      android:canRetrieveWindowContent="true"
      android:packageNames="com.instagram.android"
      android:notificationTimeout="100"/>
  ```

### Verification
```bash
cd android && ./gradlew assembleDebug 2>&1 | grep -i error
```

### Exit Criteria
- `assembleDebug` succeeds
- Manifest contains all permission entries and both service entries

---

## Step 3 — PDF Persistence Module (RN Layer)

**Model:** default  
**Reversible:** yes  
**Parallel with:** Step 4 after Steps 1+2 done

### Context
User selects PDF once; app remembers URI via AsyncStorage. Pure React Native — no native module needed.

### Tasks
- [ ] Create `src/storage/pdfStorage.ts`:
  ```typescript
  import AsyncStorage from '@react-native-async-storage/async-storage';

  const PDF_URI_KEY = '@no_scroll_pdf_uri';

  export async function getSavedPdfUri(): Promise<string | null> {
    return AsyncStorage.getItem(PDF_URI_KEY);
  }

  export async function savePdfUri(uri: string): Promise<void> {
    await AsyncStorage.setItem(PDF_URI_KEY, uri);
  }

  export async function clearPdfUri(): Promise<void> {
    await AsyncStorage.removeItem(PDF_URI_KEY);
  }
  ```
- [ ] Create `src/hooks/usePdfUri.ts` — React hook wrapping storage with loading/error state

### Verification
```bash
npx tsc --noEmit
```

### Exit Criteria
- No TypeScript errors
- `getSavedPdfUri` returns `null` on first run, stored value on subsequent runs

---

## Step 4 — Native Overlay Service (Kotlin)

**Model:** strongest (Kotlin interop with RN bridge)  
**Reversible:** yes  
**Parallel with:** Step 3 after Steps 1+2 done

### Context
Core feature: draw a book icon at the exact pixel coordinates of Instagram's Reels tab button using SYSTEM_ALERT_WINDOW. Tap fires intent to open PDF viewer.

### Tasks
- [ ] Create `android/app/src/main/java/com/noscroll/OverlayService.kt`:
  - Extends `Service`
  - Creates `WindowManager.LayoutParams` with `TYPE_APPLICATION_OVERLAY`
  - Renders `TextView` with book emoji or a vector drawable as the icon
  - Positions using `x`/`y`/`width`/`height` passed via Intent extras
  - `OnClickListener` → `startActivity(Intent(this, MainActivity::class.java))` with extra `action = "OPEN_PDF"`
  - Methods: `startOverlay(x, y, w, h)` and `stopOverlay()`
- [ ] Create `android/app/src/main/res/drawable/ic_book.xml` (vector drawable) as fallback to emoji

### Verification
```bash
cd android && ./gradlew assembleDebug
# Manual: adb shell am startservice with test coordinates, verify icon appears
```

### Exit Criteria
- Service starts without crash
- Book icon renders at specified coordinates
- Tapping icon fires Intent to MainActivity

---

## Step 5 — Native Accessibility Service (Kotlin)

**Model:** strongest  
**Reversible:** yes

### Context
AccessibilityService monitors Instagram window. Finds Reels tab button in the view hierarchy, extracts screen coordinates, starts OverlayService with those coordinates.

### Tasks
- [ ] Create `android/app/src/main/java/com/noscroll/NoScrollAccessibilityService.kt`:
  ```kotlin
  class NoScrollAccessibilityService : AccessibilityService() {

      override fun onAccessibilityEvent(event: AccessibilityEvent) {
          if (event.packageName != "com.instagram.android") {
              stopOverlayService()
              return
          }
          val reelsNode = findReelsButton(rootInActiveWindow) ?: return
          val rect = Rect()
          reelsNode.getBoundsInScreen(rect)
          startOverlayService(rect.left, rect.top, rect.width(), rect.height())
          reelsNode.recycle()
      }

      private fun findReelsButton(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
          root ?: return null
          // Primary: content-description contains "Reels"
          val byDesc = root.findAccessibilityNodeInfosByText("Reels")
          if (byDesc.isNotEmpty()) return byDesc.first()
          // Fallback: traverse bottom navigation bar, pick center button
          return findBottomNavCenter(root)
      }

      private fun startOverlayService(x: Int, y: Int, w: Int, h: Int) {
          startService(Intent(this, OverlayService::class.java).apply {
              putExtra("x", x); putExtra("y", y)
              putExtra("w", w); putExtra("h", h)
          })
      }

      private fun stopOverlayService() {
          stopService(Intent(this, OverlayService::class.java))
      }

      override fun onInterrupt() { stopOverlayService() }
  }
  ```
- [ ] Implement `findBottomNavCenter()` fallback: find the bottom navigation bar by position (bottom 10% of screen), return the center of 5 evenly-spaced buttons

### Verification
- Enable Accessibility Service in Android Settings
- Open Instagram: book icon appears over Reels tab
- Switch apps: icon disappears
- Return to Instagram: icon reappears

### Exit Criteria
- Book icon appears within 500ms of Instagram opening
- Icon disappears when leaving Instagram
- Coordinates visually match Reels button position

---

## Step 6 — React Native PDF Viewer Screen

**Model:** default  
**Reversible:** yes

### Context
Full-screen PDF reader. Launched when overlay book icon is tapped. First launch: auto-opens file picker. Subsequent launches: loads last saved PDF. FAB lets user swap PDF at any time.

### Tasks
- [ ] Create `src/screens/PdfViewerScreen.tsx`:
  ```tsx
  import Pdf from 'react-native-pdf';
  import DocumentPicker from 'react-native-document-picker';
  import { getSavedPdfUri, savePdfUri } from '../storage/pdfStorage';

  export function PdfViewerScreen() {
    const [pdfUri, setPdfUri] = useState<string | null>(null);
    const [page, setPage] = useState(1);

    useEffect(() => {
      getSavedPdfUri().then(uri => {
        if (uri) { setPdfUri(uri); }
        else { pickPdf(); }
      });
    }, []);

    async function pickPdf() {
      try {
        const result = await DocumentPicker.pickSingle({ type: [DocumentPicker.types.pdf] });
        await savePdfUri(result.uri);
        setPdfUri(result.uri);
      } catch (e) {
        if (!DocumentPicker.isCancel(e)) { throw e; }
      }
    }

    return (
      <View style={StyleSheet.absoluteFill}>
        {pdfUri && (
          <Pdf
            source={{ uri: pdfUri, cache: true }}
            page={page}
            onPageChanged={(p) => setPage(p)}
            style={StyleSheet.absoluteFill}
          />
        )}
        <FAB onPress={pickPdf} />
      </View>
    );
  }
  ```
- [ ] Create `src/components/FAB.tsx` — circular floating button, bottom-right, "change book" icon
- [ ] Wire `App.tsx`: check incoming Intent extra `action === "OPEN_PDF"`, route to `PdfViewerScreen`

### Verification
```bash
npx tsc --noEmit
npx react-native run-android
```
- First launch: file picker auto-opens
- Second launch: PDF loads immediately, no picker
- FAB tap: picker opens, new PDF loads

### Exit Criteria
- PDF renders all pages without crash
- Page position maintained during session
- PDF URI persists across full app restarts

---

## Step 7 — Permission Request Flow (First-Run UX)

**Model:** default  
**Reversible:** yes

### Context
SYSTEM_ALERT_WINDOW and Accessibility Service require manual grants in Android Settings. Need a guided setup screen so users don't have to find these themselves.

### Tasks
- [ ] Create native module `android/app/src/main/java/com/noscroll/PermissionModule.kt`:
  - Exposes to RN: `hasOverlayPermission(): Boolean` — uses `Settings.canDrawOverlays()`
  - Exposes to RN: `hasAccessibilityEnabled(): Boolean` — checks `AccessibilityManager` enabled services list
- [ ] Create `src/native/PermissionModule.ts` — TypeScript bridge for the native module
- [ ] Create `src/screens/SetupScreen.tsx`:
  - Step 1 card: "Allow Display Over Other Apps" — button deep-links to `Settings.ACTION_MANAGE_OVERLAY_PERMISSION`
  - Step 2 card: "Enable NoScroll Accessibility Service" — button deep-links to `Settings.ACTION_ACCESSIBILITY_SETTINGS`
  - Each card shows a green checkmark when permission is granted (poll on `AppState` change)
  - "Done" button appears when both are granted, navigates away
- [ ] `App.tsx` startup logic: check both permissions → show `SetupScreen` if incomplete, else show idle state

### Verification
- Fresh install: SetupScreen appears with both steps unchecked
- Grant overlay permission: Step 1 shows checkmark within 1s of returning to app
- Grant accessibility: Step 2 shows checkmark
- Both granted: SetupScreen dismissed automatically

### Exit Criteria
- Setup completes without ADB commands
- App correctly detects both permission states on resume

---

## Step 8 — Build & Sideload APK

**Model:** default  
**Reversible:** yes

### Context
Produce a debug APK for sideloading. No Play Store release required.

### Tasks
- [ ] Build debug APK:
  ```bash
  cd android && ./gradlew assembleDebug
  # Output: android/app/build/outputs/apk/debug/app-debug.apk
  ```
- [ ] Verify APK size is reasonable (<100MB)
- [ ] Test sideload:
  ```bash
  adb install -r android/app/build/outputs/apk/debug/app-debug.apk
  ```
- [ ] Create `README.md` with:
  - Prerequisites (Node 18+, Java 17, Android SDK, ADB)
  - Build steps
  - First-run permission grant walkthrough (screenshots optional)
  - Tested Instagram version
  - Caveat: Instagram UI updates may change accessibility tree — re-find Reels selector if broken

### Verification
```bash
cd android && ./gradlew assembleDebug && ls -lh app/build/outputs/apk/debug/
```

### Exit Criteria
- APK installs on physical Android device
- End-to-end: open Instagram → book icon appears → tap → PDF loads → FAB swaps PDF

---

## Invariants (must hold after every step)

- `npx tsc --noEmit` exits 0
- `./gradlew assembleDebug` exits 0
- No hardcoded file paths (all URIs from picker/AsyncStorage)
- No permissions used beyond Manifest declarations

---

## Known Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| Instagram updates change Reels content-desc | Step 5: `findBottomNavCenter()` fallback |
| `SYSTEM_ALERT_WINDOW` revoked by battery optimizer | OverlayService restarts on `onTaskRemoved`; SetupScreen re-checks on resume |
| react-native-pdf fails on some encodings | Catch render error, show "Unsupported PDF" with option to re-pick |
| Android 13+ scoped storage | DocumentPicker returns `content://` URIs — no file path needed |
| Instagram blocks a11y tree traversal | Fall back to coordinate-based detection (bottom 10% of screen, center icon) |

---

## Parallel Execution Map

```
[Step 1: Scaffold] --> [Step 2: Manifest] --> [Step 3: Storage (RN)]  --> [Step 6: PDF Screen]
                                          |                                        |
                                          --> [Step 4: Overlay Svc (Kotlin)]       |
                                                        |                          |
                                              [Step 5: A11y Svc (Kotlin)]          |
                                                        |                          |
                                              [Step 7: Permissions UX] <----------+
                                                        |
                                              [Step 8: Build & APK]
```
