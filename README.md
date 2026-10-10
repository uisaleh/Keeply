# Keeply

**Your things. Your records. Your reminders.**

Native Android app (Kotlin, Jetpack Compose, Room, WorkManager, Firebase). Modern UI:
off-white canvas, rounded white cards, colour-coded item types, Plus Jakarta Sans headings
with Inter body text, full light and dark themes.

## Status: Phase 1 + Phase 2 (core vault, manual entry)

| Area | State |
| --- | --- |
| Project structure, theme, navigation, onboarding, settings | Done |
| Room database (items + FTS4 search, receipts, warranties, subscriptions, links, tags, reminders) | Done |
| Receipts — manual entry, line items, edit, delete | Done (photos + OCR: Phase 3) |
| Warranties — duration or end date, status, reminders, created from a receipt | Done |
| Subscriptions — all cycles incl. custom, monthly/yearly totals per currency, reminders, auto roll-forward | Done |
| Saved links — URL entry, **Android Share Sheet**, polite title lookup, tags, collections, favourites | Done |
| Global search (offline, FTS4, prefix matching across all types) | Done |
| Reminder notifications (WorkManager, per-category toggles, contextual permission) | Done |
| Free-plan limits + contextual upgrade sheet | Done |
| Dashboard, Vault filters, Activity timeline, empty states | Done |
| Documents vault, screenshot intelligence, attachments | Not started — needs the encrypted file layer (Phase 3/5) |
| Google Play Billing + server verification | Not started — Premium screen states plainly it isn't on sale |
| Biometric lock, app-lock timeout, FLAG_SECURE | Not started (Phase 5) |
| Accounts (Firebase Auth: email + password, Google) and per-user cloud sync (Firestore) | Done — needs `app/google-services.json`, see below |
| Export | Not started |

Nothing in the UI pretends otherwise: there are no dead buttons, fake purchases,
fake OCR or claimed encryption. Unbuilt types are hidden from the Add menu and filters.

## Build

Requirements: Android Studio Ladybug (2024.2) or newer, JDK 17+.

```bash
./gradlew :app:assembleDebug      # debug build, seeds clearly-marked demo data
./gradlew :app:testDebugUnitTest  # domain unit tests
./gradlew :app:assembleRelease    # release: no demo data, R8 enabled
```

Pinned toolchain: AGP 8.7.3, Kotlin 2.1.0, KSP 2.1.0-1.0.29, Compose BOM 2024.12.01,
Room 2.6.1, Navigation 2.8.5, compileSdk/targetSdk 35, minSdk 26.

> **Verification note.** The 16 domain unit tests (subscription maths, warranty status,
> reminder planning, free-plan policy, FTS sanitising, URL parsing, money parsing, title
> extraction) were compiled and run with kotlinc 2.1.0. The Android/Compose layer was
> written against the pinned APIs but has **not yet been through a Gradle build** — the
> environment it was written in can't reach Google's Maven repository. Run
> `assembleDebug` first and fix anything it flags before starting Phase 3.

## Firebase setup (required)

1. Create a Firebase project and add Android apps `app.keeply.debug` and `app.keeply` with their SHA-1 fingerprints.
2. Download `google-services.json` into `app/`.
3. Authentication: enable **Email/Password** and **Google**. Firestore: create a database.
4. Publish the rules in [`firestore.rules`](firestore.rules) (each user can only access `users/{uid}/…`).

Each account's vault is kept in Room on the device and synced to `users/{uid}/items/{itemId}`.
Signing out uploads pending changes, then clears the device.

## Try it

1. Launch → four onboarding pages → **Get started** → create an account (email or Google).
2. Tap **Add → Receipt**, save it, answer **Yes** to "Does this product have a warranty?" — the warranty form is prefilled.
3. Share any page from Chrome → **Save to Keeply**.
4. Debug builds include an Adobe subscription renewing tomorrow, so a reminder fires on the first worker run.

See [ARCHITECTURE.md](ARCHITECTURE.md) for the screen map, data model and phase plan.

## Licences

Plus Jakarta Sans and Inter are bundled under the SIL Open Font License (`/licenses`).
