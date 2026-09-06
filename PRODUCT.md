# NoScroll - Product Description

## What It Is

NoScroll is an Android app that lets users replace distracting Instagram scrolling with an activity of their choice. It combines:

- A blocker for distracting Instagram scroll surfaces.
- A configurable blocker destination: the built-in reader or another installed, launchable app.
- A full PDF library and reader for any imported PDF.
- A quote-sharing workflow that turns passages into polished image cards for friends.

Reading is optional. The blocker creates a pause and opens the user's chosen destination; the reader and quote cards remain available as standalone features.

---

## Current Product Position

NoScroll no longer depends on intercepting only the Reels button. Current behavior is broader:

- It can block whole distracting Instagram content regions.
- It can still show a smaller destination entry point where appropriate.
- Both clickable overlays open the saved destination: the NoScroll reader by default, or another launchable app chosen by the user.
- No PDF import is required to choose an external app.
- It supports reading any PDF the user imports.
- It supports highlights, notes, OCR fallback, and quote sharing.

---

## Instagram Blocker

NoScroll uses two Android capabilities:

1. **Accessibility Service** - watches Instagram UI state while Instagram is foregrounded.
2. **Display Over Other Apps** - draws the NoScroll blocker or destination entry point over Instagram.

Implemented blocker behavior:

- Blocks Home feed scroll regions.
- Blocks Reels surfaces.
- Uses content-region bounds rather than only a button coordinate.
- Allows useful non-scroll states such as search typing/results and profile pages where detected.
- Suppresses the overlay during Story viewing.
- Supports Instagram and Instagram Lite package names.
- Rolls back blocked scroll attempts where supported by the accessibility tree.
- Uses a foreground overlay service with permission checks to avoid restart loops when overlay permission is missing.

The blocker is a habit boundary: when the user hits a scroll surface, NoScroll offers their chosen alternative without requiring reading.

### Choosing the destination

The library includes **Blocker opens / Change app**, even with an empty library. The full blocker has a **Change app** button, and holding either clickable overlay opens the same searchable picker. Selection saves locally and applies to the next blocker tap; canceling leaves the current choice unchanged.

Users may choose the NoScroll reader or any other enabled, launchable app visible in the current Android profile. The picker is not restricted to reading or productivity categories. NoScroll itself is represented by the reader option. Selecting Instagram does not disable its blocking rules.

The blocker displays the destination name and app icon where available. If an external destination cannot be opened, the picker explains the problem and lets the user choose again rather than silently forcing reading. Directly opening NoScroll still opens its library, so settings and books remain accessible.

App discovery uses a scoped launcher-intent package query, not `QUERY_ALL_PACKAGES`. App lists and the selected package are not uploaded. See [implementation and device regression checklist](docs/blocker-destination.md).

---

## PDF Library

A local library of imported PDF books. Books are copied into app-private storage so they remain available after import.

Implemented library features:

- Import PDFs from Android file picker.
- Receive PDFs from Android share sheet.
- Open any imported PDF.
- Track last-opened and reading progress.
- Thumbnail/progress-oriented library UI.
- Favorites/highlights-aware library filters where available in UI.
- Long-press book actions where implemented.
- Online book search/download support through configured public-book APIs.

---

## PDF Reader

The reader is built on AndroidX PDF and is meant to be useful even without Instagram.

Implemented reader features:

- Full-screen PDF reading.
- Open any imported PDF.
- Persistent page progress per book.
- Page jump.
- Metadata bar for title/author when known.
- Zen mode to hide reader chrome.
- Native PDF text selection where the document exposes text.
- OCR page fallback for scanned/non-selectable PDFs.
- Current-page sharing.

Selection actions:

- **Highlight** - save selected text and page-bound highlight data.
- **Annotate** - attach a note to a saved highlight.
- **Quote** - open the quote-card creator.
- **Share** - share selected/current content where available.

---

## Highlights And Notebook

Highlights persist in Room and are tied to the source PDF.

Implemented highlight features:

- Store book URI, page number, selected text, normalized text, PDF bounds, color, and timestamp.
- Render saved highlights back into the PDF where AndroidX PDF supports highlight overlays.
- Tap saved highlights in the reader to edit/share/delete.
- Highlight list dialog with jump, note edit, share, and delete actions.
- Notebook screen for cross-book saved quote/highlight browsing.
- Export highlights action.

---

## Quote Card Creator

NoScroll can turn selected PDF text into a shareable image card. This is a major product surface, not an accessory.

Quote-card input:

- Quote text.
- Book title and author when known.
- Page number.
- Source PDF URI for history.
- User-selected style.

Implemented style system:

| Style | Category | Background |
|-------|----------|------------|
| Parchment Editorial | Classic | Warm paper gradient |
| Midnight Library | Classic | Dark library gradient |
| Forest Margin | Classic | Deep green centered card |
| Ocean Still | Scenic | Procedural ocean scene |
| Mountain Dawn | Scenic | Procedural mountain scene |
| Rain Window | Scenic | Procedural rain/window scene |
| Minimal Ink | Modern | Minimal light editorial style |
| Classic Bookplate | Classic | Bookplate-style paper panel |

Implemented quote-card behavior:

- Canvas-rendered 1080x1350 bitmap.
- Bounded quote and attribution layout.
- Long quote ellipsizing/clipping instead of overflow.
- Procedural scenic backgrounds, no bundled stock assets.
- Style picker with accessible radio-button semantics.
- Last selected style preference.
- Saved quote-card history using existing quote-card database storage.
- Legacy theme-name mapping for old saved cards.
- Share text fallback alongside the image.

Share destinations:

- Instagram Stories.
- Instagram Feed.
- Instagram Direct.
- Messages.
- Generic Android share sheet.

---

## Book Metadata

NoScroll can derive and store book display names used by the reader, notebook, and quote cards.

Implemented sources include:

- Existing/imported file information.
- Embedded or derived metadata where available.
- Cover-page OCR heuristics.
- Online lookup through Google Books/Open Library style APIs when enabled by the user flow.

---

## UI / Design System

- Kotlin Android app.
- Jetpack Compose for major app surfaces such as library/notebook/quote preview and the blocker app picker.
- XML/View interop for reader host and overlay layouts.
- Material 3 where required by AndroidX PDF viewer components.
- Paper-inspired reading UI with warm surfaces and restrained controls.

---

## Architecture

| Layer | Technology |
|-------|------------|
| App platform | Native Android / Kotlin |
| UI | Jetpack Compose + XML/View interop |
| PDF rendering and text selection | AndroidX PDF |
| OCR | ML Kit text recognition |
| Local storage | Room + app-private file storage |
| Metadata/network | OkHttp + book metadata APIs |
| Blocker | AccessibilityService + foreground OverlayService |
| Blocker destination | SharedPreferences + launcher-intent package discovery + shared launch router |
| Sharing | Canvas bitmap + FileProvider URI + Android intents |

---

## Permissions Required

| Permission | Why |
|------------|-----|
| Accessibility Service | Detect supported Instagram surfaces and blocker state |
| SYSTEM_ALERT_WINDOW | Draw blocker or destination entry point over Instagram |
| Foreground service | Keep overlay service alive while Instagram is active |
| Storage/file access via picker | Import user-selected PDFs |
| INTERNET | Optional book metadata lookup and online book search |

The blocker app picker adds scoped package visibility, not a new runtime permission.

---

## Value Proposition

NoScroll turns the scroll reflex into a deliberate choice. Users can switch to an app they choose or read with the built-in PDF reader. Reading remains fully supported, including saved progress, useful passages, and shareable quotes, without being mandatory.
