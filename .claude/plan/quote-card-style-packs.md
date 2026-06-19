# Implementation Plan: Quote Card Style Packs

## Task Type
- [x] Android UI / quote-share feature
- [x] Rendering/data model upgrade
- [ ] Backend/network

## Context
Current quote sharing is implemented in:

| File | Role |
|---|---|
| `app/src/main/java/com/noscroll/quote/QuoteCardTheme.kt` | Gradient color enum |
| `app/src/main/java/com/noscroll/quote/QuoteCardSpec.kt` | Quote render input |
| `app/src/main/java/com/noscroll/quote/QuoteCardBitmapBuilder.kt` | Canvas renderer for 1080x1350 card |
| `app/src/main/java/com/noscroll/quote/QuoteCardPreviewActivity.kt` | Compose preview, style picker, save/share |
| `app/src/main/java/com/noscroll/data/QuoteCardEntity.kt` | Saved quote-card history |
| `app/src/main/java/com/noscroll/quote/ShareBottomSheet.kt` | Share destination picker |

Current design is one renderer with six gradient themes. User wants richer preloaded designs: font choices, backgrounds, scenic options, and stronger style defaults.

## Technical Solution
Replace the narrow `QuoteCardTheme` enum with richer immutable preset specs: `QuoteCardStylePack`.

Each style pack defines:
- stable id and display name
- background type: gradient, solid/texture, or bundled scenic bitmap
- font family/style for quote and attribution
- text color, attribution color, accent color
- layout preset: editorial centered, classic left rail, scenic overlay, minimal poster
- quote box treatment: none, translucent scrim, paper panel, or soft glass panel
- max quote chars and adaptive text-size bounds

No downloaded images for v1. Bundle a small curated set of local `drawable-nodpi` scenic assets or procedural generated textures so sharing works offline and remains deterministic.

Use Liquid Glass lightly: only preview controls/scrim style where it improves readability. Do not make Android UI pretend to be iOS 26; use glass-like translucent panels as a NoScroll style, not platform mimicry.

## Style Packs
Ship 8 presets:

1. `Parchment Editorial` - warm paper, serif italic, left accent rail.
2. `Midnight Library` - dark graphite gradient, serif quote, gold accent.
3. `Forest Margin` - deep green, readable cream text.
4. `Ocean Still` - scenic water/sky background, dark bottom scrim.
5. `Mountain Dawn` - scenic mountain background, translucent paper panel.
6. `Rain Window` - moody blurred scenic background, centered glass panel.
7. `Minimal Ink` - off-white solid, bold sans title-like composition.
8. `Classic Bookplate` - border, centered quote marks, small metadata footer.

## Implementation Steps

1. Add style model
   - Create `QuoteCardStylePack.kt`.
   - Keep `QuoteCardTheme` temporarily as compatibility shim or migrate enum names to style ids.
   - Add sealed/background config:
     ```kotlin
     sealed interface QuoteBackground {
         data class Gradient(val start: Int, val end: Int) : QuoteBackground
         data class Image(@DrawableRes val resId: Int, val overlayArgb: Int) : QuoteBackground
     }
     enum class QuoteLayout { EDITORIAL, CENTERED, SCENIC_PANEL, MINIMAL }
     ```

2. Add bundled assets
   - Add scenic images to `app/src/main/res/drawable-nodpi/`:
     - `quote_bg_ocean_still.jpg`
     - `quote_bg_mountain_dawn.jpg`
     - `quote_bg_rain_window.jpg`
   - Keep assets around 1080x1350 or 1440x1800, compressed JPEG/WebP.
   - License-safe only: self-generated or permissive assets with source tracked in `artifacts/` or comments.

3. Extend `QuoteCardSpec`
   - Replace `theme: QuoteCardTheme` with `styleId: String`.
   - Resolve style through `QuoteCardStyles.byId(styleId)`.
   - Keep constructor/helper to map old `themeName` values to new ids for saved quotes.

4. Rewrite renderer by style sections
   - Update `QuoteCardBitmapBuilder.build(context, spec)` because image backgrounds need resource decode.
   - Split into functions:
     - `drawBackground(canvas, style)`
     - `drawQuotePanel(canvas, style)`
     - `drawQuoteText(canvas, spec, style)`
     - `drawAttribution(canvas, spec, style)`
     - `drawWatermark(canvas, style)`
   - Add adaptive fitting that measures whole layout, shrinks font, and ellipsizes only after size floor.
   - Add tests for `wrapText`, long words, long quote truncation, and old theme-name migration.

5. Upgrade preview picker
   - In `QuoteCardPreviewActivity`, replace circular color chips with horizontal style cards.
   - Each card shows miniature background + title + small typography sample.
   - Add tabs/segmented control if needed:
     - `All`
     - `Scenic`
     - `Classic`
   - Default first open: `Parchment Editorial`, but remember last selected style in SharedPreferences.

6. Persist selected style
   - Store new `styleId` in existing `QuoteCardEntity.themeName` for now to avoid DB migration.
   - Rename later only if broader schema cleanup happens.
   - When saving, write style id, not display name.

7. Share text fallback
   - Pass `shareText` into `ShareBottomSheet.newInstance(bitmap, shareText = quote + attribution)`.
   - Keeps Messages/More useful when image previews fail or recipient app prefers text.

8. Visual QA pass
   - Generate sample cards for:
     - 40-char quote
     - 250-char quote
     - 600-char quote
     - long author/title
   - Verify no overflow, unreadable scenic text, clipped attribution, or watermark collision.

9. Build and graph update
   - Run `.\gradlew.bat testDebugUnitTest assembleDebug`.
   - Run `graphify update .` after code changes.

## Key Files

| File | Operation | Description |
|---|---|---|
| `app/src/main/java/com/noscroll/quote/QuoteCardStylePack.kt` | Add | New design preset model/catalog |
| `app/src/main/java/com/noscroll/quote/QuoteCardTheme.kt` | Modify | Compatibility mapping or removal after migration |
| `app/src/main/java/com/noscroll/quote/QuoteCardSpec.kt` | Modify | Use style id |
| `app/src/main/java/com/noscroll/quote/QuoteCardBitmapBuilder.kt` | Modify | Background images, layouts, typography |
| `app/src/main/java/com/noscroll/quote/QuoteCardPreviewActivity.kt` | Modify | Rich style picker and last-style preference |
| `app/src/main/java/com/noscroll/quote/ShareBottomSheet.kt` | Modify | Include text fallback |
| `app/src/main/res/drawable-nodpi/*` | Add | Scenic backgrounds |
| `app/src/test/java/com/noscroll/quote/*` | Add | Renderer/layout unit tests |

## Risks and Mitigation

| Risk | Mitigation |
|---|---|
| Scenic background makes text unreadable | Force per-style overlay/scrim and contrast test samples |
| Bitmap decoding increases memory | Decode to exact 1080x1350 target, recycle intermediate bitmaps |
| Saved quote themes break | Map old enum names to new style ids |
| App size grows | Use 3 compressed WebP/JPEG assets first, no large pack |
| Canvas text overflows | Adaptive font fitting + max lines + tests |
| Generic "AI design" feel | Use editorial/bookish system: paper, margins, readable type, restrained scenic options |

## Acceptance Criteria

- Quote preview opens with better default design.
- User can choose at least 8 preloaded styles, including 3 scenic backgrounds.
- Existing saved quote cards still render.
- Share output is 1080x1350 and readable for short/medium/long quotes.
- Style selection persists for next quote card.
- Unit tests pass and `assembleDebug` succeeds.

## Pseudo-Code

```kotlin
data class QuoteCardStylePack(
    val id: String,
    val name: String,
    val background: QuoteBackground,
    val layout: QuoteLayout,
    val quoteTypeface: TypefaceSpec,
    val attributionTypeface: TypefaceSpec,
    val textColor: Int,
    val attributionColor: Int,
    val accentColor: Int,
    val panel: QuotePanel
)

object QuoteCardStyles {
    val all = listOf(parchmentEditorial, oceanStill, mountainDawn, ...)

    fun byId(id: String): QuoteCardStylePack =
        all.firstOrNull { it.id == id } ?: legacyThemeMap[id] ?: parchmentEditorial
}

object QuoteCardBitmapBuilder {
    fun build(context: Context, spec: QuoteCardSpec): Bitmap {
        val style = QuoteCardStyles.byId(spec.styleId)
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawBackground(context, canvas, style)
        drawPanel(canvas, style)
        val textBounds = drawAdaptiveQuote(canvas, spec, style)
        drawAttribution(canvas, spec, style, textBounds)
        drawWatermark(canvas, style)
        return bitmap
    }
}
```

## SESSION_ID
- CODEX_SESSION: not generated; no external model wrapper available in current Codex toolset.
- GEMINI_SESSION: not generated; no external model wrapper available in current Codex toolset.

