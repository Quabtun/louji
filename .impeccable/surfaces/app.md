---
version: 1
slug: "app"
primary_target: "app"
related_targets: []
---

# Surface: 楼记 Android app

Mode: Operate (the rider completes a record or looks one up; expression lives in precision and native detail, not spectacle).

Audience: a delivery rider, one-handed, at a building entrance, often at night, needing a record in under 30 seconds and a lookup before climbing.

Scope: the whole native Android app: record list, record form, record detail, media viewer, camera, settings, update sheet. Platform: android. Stack: Kotlin + Jetpack Compose, Material 3.

Constraints: local-only data, Chinese UI, API 24+, CameraX media, GitHub release updates, 48dp targets, dark and light first-class, font scale 1.3, no accounts.

## Direction contract

THESIS: A rider's field ledger, not a generic notes app. The climb decision (要爬楼 / 不用爬楼) is the spine of every screen; the floor/room readout is the largest number and the waybill-like record strip is the repeating unit. It refuses the generic card-feed and the delivery-platform dashboard.

OWN-WORLD: Concrete-paper light ground and night-ink dark ground; safety amber as the single action/attention accent; signal green reserved for 免爬 and 会下来拿; ink and warm white for text. Ledger rules, stamp-like status plates, tabular numerals, and a large condensed-feeling floor readout built from Roboto Black. Material 3 components, tonal surfaces, no glass, gradients, or arbitrary shadows.

STORY: A rider opens the app, sees this month's count and the last record at a glance, searches by order/tail/place, and immediately knows whether the next visit means stairs, whether the customer comes down, and what the door is like. Creating a record is a short sequence of large, unambiguous choices; adding evidence is one tap into camera or gallery. The app feels like something that belongs in a delivery bag, not a startup demo.

FIRST VIEWPORT: Top: 楼记 wordmark with settings; a compact ledger strip showing 本月 records / 免爬 / 会下楼. Then the search field and filter chips, then the first record card with a stamped 免爬/要爬 verdict, a large floor/room code, order tail and media count. FAB bottom-right for 新建. On an empty phone the strip reads 0 and a single typographic empty state sits where the first card will be.

FORM: Code-led native Android, Material 3 conventions. The signature interaction is the two-position climb decision at the top of the form: tapping 要爬楼 / 不用爬楼 snaps a stamped plate into place with haptic confirmation and reveals only the relevant door/access prompts. List-to-detail uses a shared-axis transition. Motion respects the system animation scale.

seed: none (unavailable)

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance
