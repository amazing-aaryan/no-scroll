# NoScroll — Product Description

## What It Is

NoScroll is an Android app that replaces the habit of mindlessly scrolling Instagram Reels with intentional reading. When you would tap the Reels button, NoScroll intercepts and redirects you to your book.

---

## Core Mechanism: Instagram Interception

NoScroll uses two Android system permissions to intercept Instagram:

1. **Accessibility Service** — watches Instagram's UI tree in real time. Detects when the Reels tab button is visible (by view ID, language-independent). Polls every 250ms while Instagram is in the foreground.
2. **Display Over Other Apps** — draws a floating overlay directly on top of the Reels tab button.

When Reels is detected, the overlay covers that exact button region with a book icon. Tapping it launches the reader instead of Reels.

The service also handles:
- Story viewer detection (suppresses overlay during story viewing for 30 seconds)
- Full-page feed block mode (covers entire Instagram feed region when active)
- Adaptive positioning relative to Instagram's navigation bar, regardless of device or app layout version
- Support for both Instagram and Instagram Lite packages

---

## PDF Library

A local library of imported PDF books. Books are stored in app-private storage.

**Library features:**
- Import PDFs from any source (file picker, share sheet)
- Filter by: All, Favorites, Highlights
- Sort by: Recent, Title, Author, Added
- Thumbnail preview per book (cached)
- Progress indicator (pages read / total pages)
- Long-press context menu: open, favorite, identify (metadata lookup), delete
- Online book search and download (via configured legal books API — Open Library compatible)

---

## PDF Reader

Full-featured reader built on Android's `PdfRenderer` and `androidx.pdf`.

**Reader features:**
- Paginated view with smooth page turning
- Text selection via the native PDF text layer
- **Zen mode**: hides all UI chrome for distraction-free reading
- Page number jump dialog
- Metadata bar showing book title/author at top
- Persistent reading position (saved per book)

**Text actions on selection:**
- **Highlight** — saves the selection as a persistent highlight (stored in Room DB, linked to page + book)
- **Quote** — opens the quote card creator
- **Annotate** — attach a freeform note to the selection

---

## Highlights System

All highlights persist to a Room database.

- Each highlight stores: book ID, page number, selected text, normalized text, bounding rect, timestamp
- Highlights visible inline in the reader (colored overlay on text)
- Exportable via Export Highlights action
- Viewable across all books in the Notebook

---

## Quote Card Creator

Highlight any text → "Quote" → generates a shareable image card.

**Card contents:**
- The highlighted quote text
- Book title and author (from metadata)
- Page number
- Decorative accent line and yin-yang icon

**6 visual themes:**
| Theme | Background | Text | Feel |
|-------|-----------|------|------|
| Parchment | Warm cream gradient | Dark brown | Classic paper |
| Midnight | Near-black blue-gray | Off-white | Elegant dark |
| Dusk | Deep purple → magenta | Soft peach | Moody warm |
| Ocean | Deep navy | Ice blue | Calm cool |
| Forest | Deep green | Pale green | Natural |
| Clay | Warm tan | Dark brown | Earthy |

**Dynamic font scaling:** Quote text scales down automatically based on character count so the full quote always fits without overlap, preserving card proportions.

**Share destinations:**
- Instagram Stories (direct intent)
- Instagram Feed
- Instagram Direct
- Messages (SMS/MMS)
- Generic Android share sheet

---

## Notebook

Cross-book view of all highlights and annotations.

- Tabbed by book
- Shows quote text, page number, source book
- Tap any entry to jump directly to that page in the reader
- Share any highlight as a quote card from here

---

## Book Metadata

NoScroll automatically identifies books and fetches rich metadata.

**Sources (in priority order):**
1. **Embedded PDF metadata** — title/author from the PDF's XMP/document info
2. **Google Books API** — search by title+author, fetches cover URL, description, ISBN
3. **Open Library API** — fallback metadata source
4. **Cover page OCR** — extracts text from the first page image when no metadata exists

**Metadata includes:** title, author, cover image URL, description, ISBN, publisher, year

Manual edit dialog lets the user correct any field.

---

## Collections

Books can be organized into named collections. Collections support custom ordering and grouping for large libraries.

---

## Tutorial System

First-run onboarding with anchored tooltip overlays.

- Tutorial steps anchored to specific UI views
- Steps walk through: granting permissions, importing first book, highlighting, making a quote card, navigating to Notebook
- Progress persisted — never shown again after completion

---

## UI / Design System

- **Jetpack Compose** throughout (library, reader toolbar, notebook, search, tutorial)
- **Paper Theme** — warm off-white backgrounds, serif-adjacent typography, cream/brown accent palette
- Material 3 components with custom color scheme

---

## Architecture

| Layer | Technology |
|-------|-----------|
| UI | Jetpack Compose + View interop |
| PDF rendering | `android.graphics.pdf.PdfRenderer` + `androidx.pdf` |
| Local storage | Room (SQLite), app-private file storage |
| Networking | OkHttp (metadata APIs, book search) |
| Background | Foreground Service (overlay), AccessibilityService |
| Image sharing | Canvas-rendered Bitmap → FileProvider URI |

---

## Permissions Required

| Permission | Why |
|-----------|-----|
| Accessibility Service | Detect Instagram Reels tab in real time |
| SYSTEM_ALERT_WINDOW | Draw book overlay on top of Instagram |
| READ_EXTERNAL_STORAGE / READ_MEDIA_IMAGES | Import PDFs from device storage |
| FOREGROUND_SERVICE | Keep overlay alive while Instagram is open |
| INTERNET | Book metadata lookup, online book search |

---

## Value Proposition

> Every time you would scroll Reels, you read instead.

NoScroll works at the OS level — no willpower required. The habit replacement is mechanical: the button you'd tap to scroll now opens your book. Paired with a full reader, highlight system, and quote sharing, it makes reading as frictionless as social media.
