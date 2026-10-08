# Inside Recall — AI-assisted feature panels

[Six-screen hero and its generation prompts](HERO.md) — the README's wide main image.

## Sources and provenance

Six portrait feature panels use real screenshots captured on October 9, 2026 from current Preview 4 source. The built-in image generation tool created each presentation using its native screenshot and two user-supplied marketing-layout references. No external AI API account was required. The references informed editorial hierarchy and focused callouts, not app functionality or branding.

These are promotional presentations, NOT untouched screenshots, pixel-identical composites or proposed UI. AI re-rendering can change small glyph shapes, spacing, system bars or chart details; linked native originals are authoritative. All subjects, cards, notes, due dates and completed-event history are isolated synthetic demonstration fixtures. Counts, dates and intervals are examples, not promises about workload or retention. No personal library is shown.

The shared visual direction uses a warm off-white background, graphite Android-style phone frame, restrained blue curves, short feature headlines and selective enlargements of existing UI. The rating and study-activity callouts are presentation overlays, not extra in-app controls. These assets are documentation only and do not increase APK size.

## Capture validation

`ReadmeScreenshotsTest.captureCurrentNativeScreensWithDemoData` passed on the API 34 Small_Phone emulator: **OK (1 test)**. `assembleDebug` and `assembleDebugAndroidTest` succeeded. The test uses in-memory Room, restores preferences, disables reminders and waits for short UI transitions to settle before capturing. Existing older captures remain available. This task does not modify production app behavior or the published APK.

## Assets and exact generation prompts

Each source is 720 × 1280. Each final panel is 1024 × 1536. Method: built-in image generation, edit/compositing, with the native screenshot first, consistent editorial-panel reference second, and feature-callout reference third. The same base prompt was combined with the feature-specific instructions below; full combined prompts are recorded for reproducibility.

### library

- Presentation: [library.png](library.png)
- Original: [native library capture](../../screenshots/preview4/readme-preview4/library.png)

```text
Use case: compositing / ads-marketing. Create ONE production-quality 1024 x 1536 portrait app-feature marketing panel for Recall's GitHub README. Image 1 is a REAL native Android screenshot and the content source; preserve all app words, numbers, symbols, dates, colors, icons, controls and their arrangement. Image 2 is a style reference for consistent premium app-store editorial panels, image 3 a style reference for one focused feature callout; do not copy either brand or invent their features. Use one coherent Recall visual language: warm off-white #F7F7F5 background, restrained steel-blue #315F87 curve entering one bottom corner, graphite #1B1D20 clean modern sans typography, subtle powder-blue #DCEBFA accents. At top a short strong two-line headline with one quieter subtitle and small 'Recall' wordmark. Below, one large crisp front-facing slim graphite ANDROID phone mockup, centered punch-hole (not iPhone notch/Apple logo), containing the source screenshot. Soft grounded shadow, generous margins, readable screen; the entire phone stays visible. No extravagant tilt, busy scene, gradients, glowing outlines, fake charts, awards, reviews, ranking badges, hands or objects. Any callout MUST be a faithful enlargement of an existing UI detail, not invented functionality. Preserve source screenshot as intact insert, not redesign. Keep Arabic glyphs and scientific superscripts accurate. Bottom small honest caption 'AI-assisted presentation · Demo data'. Output a single independent feature panel, not a multi-panel collage. Exact extra headline and subtitle text follow. Do not add other marketing claims.
Headline: 'Organize your lessons'. Subtitle: 'Subjects, lessons. Chapters optional.' No floating callout needed.
```

### resources

- Presentation: [resources.png](resources.png)
- Original: [native resources capture](../../screenshots/preview4/readme-preview4/resources.png)

```text
Use case: compositing / ads-marketing. Create ONE production-quality 1024 x 1536 portrait app-feature marketing panel for Recall's GitHub README. Image 1 is a REAL native Android screenshot and the content source; preserve all app words, numbers, symbols, dates, colors, icons, controls and their arrangement. Image 2 is a style reference for consistent premium app-store editorial panels, image 3 a style reference for one focused feature callout; do not copy either brand or invent their features. Use one coherent Recall visual language: warm off-white #F7F7F5 background, restrained steel-blue #315F87 curve entering one bottom corner, graphite #1B1D20 clean modern sans typography, subtle powder-blue #DCEBFA accents. At top a short strong two-line headline with one quieter subtitle and small 'Recall' wordmark. Below, one large crisp front-facing slim graphite ANDROID phone mockup, centered punch-hole (not iPhone notch/Apple logo), containing the source screenshot. Soft grounded shadow, generous margins, readable screen; the entire phone stays visible. No extravagant tilt, busy scene, gradients, glowing outlines, fake charts, awards, reviews, ranking badges, hands or objects. Any callout MUST be a faithful enlargement of an existing UI detail, not invented functionality. Preserve source screenshot as intact insert, not redesign. Keep Arabic glyphs and scientific superscripts accurate. Bottom small honest caption 'AI-assisted presentation · Demo data'. Output a single independent feature panel, not a multi-panel collage. Exact extra headline and subtitle text follow. Do not add other marketing claims.
Headline: 'Keep resources together'. Subtitle: 'Notes, PDFs and photos by subject.' No invented PDF or photo thumbnails; source currently shows notes.
```

### review

- Presentation: [review.png](review.png)
- Original: [native review capture](../../screenshots/preview4/readme-preview4/review.png)

```text
Use case: compositing / ads-marketing. Create ONE production-quality 1024 x 1536 portrait app-feature marketing panel for Recall's GitHub README. Image 1 is a REAL native Android screenshot and the content source; preserve all app words, numbers, symbols, dates, colors, icons, controls and their arrangement. Image 2 is a style reference for consistent premium app-store editorial panels, image 3 a style reference for one focused feature callout; do not copy either brand or invent their features. Use one coherent Recall visual language: warm off-white #F7F7F5 background, restrained steel-blue #315F87 curve entering one bottom corner, graphite #1B1D20 clean modern sans typography, subtle powder-blue #DCEBFA accents. At top a short strong two-line headline with one quieter subtitle and small 'Recall' wordmark. Below, one large crisp front-facing slim graphite ANDROID phone mockup, centered punch-hole (not iPhone notch/Apple logo), containing the source screenshot. Soft grounded shadow, generous margins, readable screen; the entire phone stays visible. No extravagant tilt, busy scene, gradients, glowing outlines, fake charts, awards, reviews, ranking badges, hands or objects. Any callout MUST be a faithful enlargement of an existing UI detail, not invented functionality. Preserve source screenshot as intact insert, not redesign. Keep Arabic glyphs and scientific superscripts accurate. Bottom small honest caption 'AI-assisted presentation · Demo data'. Output a single independent feature panel, not a multi-panel collage. Exact extra headline and subtitle text follow. Do not add other marketing claims.
Headline: 'Review with focus'. Subtitle: 'Recall first. Reveal. Rate.' Optional single faithful rating-row enlargement: Again / Hard / Good / Easy. Preserve actual displayed intervals; no invented ones.
```

### review-arabic

- Presentation: [review-arabic.png](review-arabic.png)
- Original: [native review-arabic capture](../../screenshots/preview4/readme-preview4/review-arabic.png)

```text
Use case: compositing / ads-marketing. Create ONE production-quality 1024 x 1536 portrait app-feature marketing panel for Recall's GitHub README. Image 1 is a REAL native Android screenshot and the content source; preserve all app words, numbers, symbols, dates, colors, icons, controls and their arrangement. Image 2 is a style reference for consistent premium app-store editorial panels, image 3 a style reference for one focused feature callout; do not copy either brand or invent their features. Use one coherent Recall visual language: warm off-white #F7F7F5 background, restrained steel-blue #315F87 curve entering one bottom corner, graphite #1B1D20 clean modern sans typography, subtle powder-blue #DCEBFA accents. At top a short strong two-line headline with one quieter subtitle and small 'Recall' wordmark. Below, one large crisp front-facing slim graphite ANDROID phone mockup, centered punch-hole (not iPhone notch/Apple logo), containing the source screenshot. Soft grounded shadow, generous margins, readable screen; the entire phone stays visible. No extravagant tilt, busy scene, gradients, glowing outlines, fake charts, awards, reviews, ranking badges, hands or objects. Any callout MUST be a faithful enlargement of an existing UI detail, not invented functionality. Preserve source screenshot as intact insert, not redesign. Keep Arabic glyphs and scientific superscripts accurate. Bottom small honest caption 'AI-assisted presentation · Demo data'. Output a single independent feature panel, not a multi-panel collage. Exact extra headline and subtitle text follow. Do not add other marketing claims.
Headline: 'Study in Arabic'. Subtitle: 'Cairo typography. Readable science.' Preserve native Arabic question, answer, RTL controls and [Ar] 3d⁵ exactly; no floating callout or made-up Arabic. Marketing headline remains English.
```

### insights

- Presentation: [insights.png](insights.png)
- Original: [native insights capture](../../screenshots/preview4/readme-preview4/insights.png)

```text
Use case: compositing / ads-marketing. Create ONE production-quality 1024 x 1536 portrait app-feature marketing panel for Recall's GitHub README. Image 1 is a REAL native Android screenshot and the content source; preserve all app words, numbers, symbols, dates, colors, icons, controls and their arrangement. Image 2 is a style reference for consistent premium app-store editorial panels, image 3 a style reference for one focused feature callout; do not copy either brand or invent their features. Use one coherent Recall visual language: warm off-white #F7F7F5 background, restrained steel-blue #315F87 curve entering one bottom corner, graphite #1B1D20 clean modern sans typography, subtle powder-blue #DCEBFA accents. At top a short strong two-line headline with one quieter subtitle and small 'Recall' wordmark. Below, one large crisp front-facing slim graphite ANDROID phone mockup, centered punch-hole (not iPhone notch/Apple logo), containing the source screenshot. Soft grounded shadow, generous margins, readable screen; the entire phone stays visible. No extravagant tilt, busy scene, gradients, glowing outlines, fake charts, awards, reviews, ranking badges, hands or objects. Any callout MUST be a faithful enlargement of an existing UI detail, not invented functionality. Preserve source screenshot as intact insert, not redesign. Keep Arabic glyphs and scientific superscripts accurate. Bottom small honest caption 'AI-assisted presentation · Demo data'. Output a single independent feature panel, not a multi-panel collage. Exact extra headline and subtitle text follow. Do not add other marketing claims.
Headline: 'See your consistency'. Subtitle: 'Completed reviews, day by day.' Optional faithful enlargement of the existing study activity heatmap only. Preserve its five-level legend. No invented metrics, charts, mastery or calibration scores.
```

### calendar

- Presentation: [calendar.png](calendar.png)
- Original: [native calendar capture](../../screenshots/preview4/readme-preview4/calendar.png)

```text
Use case: compositing / ads-marketing. Create ONE production-quality 1024 x 1536 portrait app-feature marketing panel for Recall's GitHub README. Image 1 is a REAL native Android screenshot and the content source; preserve all app words, numbers, symbols, dates, colors, icons, controls and their arrangement. Image 2 is a style reference for consistent premium app-store editorial panels, image 3 a style reference for one focused feature callout; do not copy either brand or invent their features. Use one coherent Recall visual language: warm off-white #F7F7F5 background, restrained steel-blue #315F87 curve entering one bottom corner, graphite #1B1D20 clean modern sans typography, subtle powder-blue #DCEBFA accents. At top a short strong two-line headline with one quieter subtitle and small 'Recall' wordmark. Below, one large crisp front-facing slim graphite ANDROID phone mockup, centered punch-hole (not iPhone notch/Apple logo), containing the source screenshot. Soft grounded shadow, generous margins, readable screen; the entire phone stays visible. No extravagant tilt, busy scene, gradients, glowing outlines, fake charts, awards, reviews, ranking badges, hands or objects. Any callout MUST be a faithful enlargement of an existing UI detail, not invented functionality. Preserve source screenshot as intact insert, not redesign. Keep Arabic glyphs and scientific superscripts accurate. Bottom small honest caption 'AI-assisted presentation · Demo data'. Output a single independent feature panel, not a multi-panel collage. Exact extra headline and subtitle text follow. Do not add other marketing claims.
Headline: 'See what’s coming'. Subtitle: 'Your next reviews, one day at a time.' Preserve October 2026 calendar, weekday alignment, selected Friday 9 with 9 cards and all existing daily counts. No floating callout necessary, never alter dates.
```
