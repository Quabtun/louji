# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Stack

Delegated and confirmed in the approved plan: native Android, Kotlin + Jetpack Compose (Material 3), Room, CameraX, Media3, OkHttp, DataStore.

## Users

The primary user is a delivery rider working Chinese residential buildings, often at night and often one-handed. They need to remember which customers and buildings require climbing stairs, which do not, and what the customer is like before the next delivery to the same place.

## Product Purpose

楼记 is a private field notebook for the rider's own deliveries. It records the building, order, customer, floor/room, door and access facts, whether stairs are required, whether the customer comes down, and the customer's communication style, together with screenshots, photos and videos. Success is a complete record in under 30 seconds and a confident answer before climbing.

## Positioning

Unlike a generic notes app, the record is structured around the climb decision and the physical facts a rider needs at the door. Unlike a delivery platform, it keeps the rider's private knowledge on the rider's own phone and is organized for fast recall, not for dispatch.

## Operating Context

- Records are made at the building entrance or immediately after a delivery, often in dim stairwells, outdoors, or while wearing gloves.
- Typical lookups happen while walking up to a building or standing at the door, one-handed.
- Media arrives from two sources: screenshots and photos already in the phone gallery, and in-app camera capture.
- The phone is personal; there are no accounts, teams, or shared servers.
- Records include customer data and must remain local unless the rider deliberately exports a backup.

## Capabilities and Constraints

- Required fields: location, order number, gender, phone tail, floor, room number, door intact, can open from above, whether stairs are unnecessary, whether the customer comes down, customer personality.
- Media: screenshot/photo import from the system picker, in-app photo and video capture, per-record gallery, full-screen viewing.
- Local-only storage with manual zip export/import; no cloud sync.
- GitHub Releases based update check, download and system installer handoff.
- Chinese UI; Android 7.0 (API 24) and above; target SDK 34.
- No account, no analytics, no ads, no app lock in the first release.

## Brand Commitments

Confirmed name: 楼记. The product voice is plain, practical rider language, never marketing hype. The identity draws on the rider's real world: waybills, floor plates and handwritten ledgers.

## Evidence on Hand

No customer data, testimonials, benchmarks or production screenshots exist yet. All demo content used during design and tests must be synthetic and clearly replaceable.

## Product Principles

1. Record fast, read faster: the climb decision and the floor/room are always the first things visible.
2. Facts over decoration: every status is written in words and reinforced by icon and color, never color alone.
3. Private by default: data stays on the phone; network use is limited to the update check.
4. Built for the doorway: large targets, one-handed reach, high contrast, dark and light themes.
5. The rider's language wins over generic app copy.

## Accessibility & Inclusion

48dp minimum touch targets, TalkBack labels for every control, sp-based type that survives a 1.3x font scale, contrast checked in both themes, no color-only status encoding, and no orientation lock.
