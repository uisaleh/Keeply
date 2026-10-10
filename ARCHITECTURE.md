# Keeply — Architecture & Screen Map

Product loop: **Capture → Organise → Remember → Act.** Every MVP feature answers one of:
what do I own · where did I save it · when does it expire · when will I be charged ·
where is the proof · what needs my attention.

## 1. Layers

```
ui/            Compose screens + ViewModels (state in, events out). No SQL, no Android services.
  theme/       Sunset Editorial tokens → KeeplyColors, KeeplyType, Space, Radius, Motion
  components/  Button, Tag, Card, TextField, DateField, Chips, SunsetStripe, PixelBlocks, StatBlock, EmptyState
  features/    onboarding, home, vault, activity, search, settings, premium, receipts, warranties, subscriptions, links
  navigation/  type-safe @Serializable routes
core/          Pure Kotlin. No Android imports. 100% unit-testable.
  model/       ItemType, Money (minor units + ISO code), Receipt, Warranty, Subscription, SavedLink
  domain/      SubscriptionMath, WarrantyRules, ReminderPlanner, FtsQuery, UrlParser, RelativeDays
premium/       EntitlementTier, EntitlementPolicy (free limits), PriceCatalog, EntitlementRepository
data/          Room (db/), repositories (repo/), DataStore settings (settings/)
notifications/ channels + ReminderWorker (WorkManager, offline)
links/         LinkMetadataFetcher (one polite request, 64 KB cap)
demo/          Debug-only seeder, guarded by BuildConfig.DEMO_DATA_ENABLED
```

Packages map 1:1 to the spec's suggested modules. They're one Gradle module today
for build speed; each package depends only "downwards" (ui → data/premium → core), so
splitting into `:core`, `:data`, `:feature-*` Gradle modules later is mechanical.
DI is a hand-written `AppContainer`; swap for Hilt if the graph grows.

## 2. Data model (Room, schema v1)

```
items (id PK, type, title, subtitle, notes, searchText, isFavorite, isArchived, isDemo, createdAt, updatedAt)
  ├─ items_fts         FTS4 contentEntity = items (title, subtitle, notes, searchText)
  ├─ receipts          itemId PK/FK  merchant, totalMinor, currency, purchaseEpochDay, category, paymentMethod
  │    └─ receipt_lines id, receiptId FK, position, description           (= ReceiptItem)
  ├─ warranties        itemId PK/FK  product, brand, model, purchase/expiry, durationMonths?, retailer, serial,
  │                    sourceReceiptId FK→receipts (SET NULL), reminderOffsets
  ├─ subscriptions     itemId PK/FK  service, amountMinor, currency, cycle, customIntervalDays?, nextBillingEpochDay,
  │                    category, paymentMethod, status, reminderOffsets
  ├─ saved_links       itemId PK/FK  url, domain, pageTitle, collection
  ├─ item_tags ── tags (unique name)
  └─ reminders         id, itemId FK, offsetDays, triggerEpochDay, delivered
```

All detail tables cascade-delete with their item. Money is never a Double; totals are
grouped by currency and never converted. Reminder offsets live on the detail row (the
user's intent); `reminders` rows are derived and re-planned on every save and roll-forward.

**Planned migrations:** v2 `documents` + `attachments` (encrypted files, Keystore key),
v3 `screenshots`, v4 `categories` (custom categories, Premium). No destructive fallback.

## 3. Screen map

```
Onboarding (4 pages) ─► Create my vault (no account)
│
└─ Bottom nav
   ├─ Home ── greeting · search entry · Upcoming payments · Expiring soon ·
   │          Subscriptions stat (month / year) · Recent captures · Free plan usage · Recently added
   ├─ Vault ── filters: All · Receipts · Warranties · Subscriptions · Links ── list ── item → edit form
   ├─ Add (sheet) ── Receipt · Warranty · Subscription · Link ──(free limit?)─► Limit sheet ─► Premium
   ├─ Activity ── reminders permission card · Upcoming (90 days) · Recently added
   └─ Settings ── plan · appearance · default currency · notifications · privacy · remove demo · delete all
Search (from Home/Vault) ── offline FTS across all types
Forms: Receipt ─(new)─► "Does this product have a warranty?" ─► Warranty (prefilled)
       Warranty · Subscription · Link (also opened by the Share Sheet and by notifications)
Premium ── plan comparison · Annual / Lifetime · "not on sale yet" until Billing ships
```

## 4. Key decisions

- **Offline-first.** Every MVP flow works without network. The only request is the optional link-title lookup.
- **Privacy.** Two permissions (INTERNET, POST_NOTIFICATIONS). No contacts/SMS/location/broad storage. Cloud backup and device transfer excluded via `data_extraction_rules`. Notifications are `VISIBILITY_PRIVATE` and contain titles and timing only. Exceptions are never logged with messages (may contain user content). No analytics SDK yet; when added, only the anonymous event names from the spec.
- **Entitlements.** UI asks `EntitlementPolicy.canAdd(...)`, never a boolean. Demo items don't count. `UsageAllowance` is separate so metered AI features never ride on the flat tier.
- **Billing (Phase 4).** BillingClient → purchase token → Keeply backend → Play Developer API verification → entitlement. Prices displayed from ProductDetails; `PriceCatalog` list prices are labelled fallbacks only.
- **Reminders.** Day-granular "N days before", WorkManager every 12 h; no exact-alarm permission. A late run states the real remaining time and skips events already past.
- **Process death.** Form drafts persist through `SavedStateHandle.saveable`; filters and search queries through `getStateFlow`.

## 5. Sunset Editorial → Android mapping

| Token | Android |
| --- | --- |
| brand-primary #fa520f | `accent` — Primary button, Add tab, progress, focus border |
| brand-primary-deep / link | orange **text** only (5.03:1) |
| cream / surface-warm | form panels, empty states, plan card — no border, no shadow |
| ink / btn-dark | secondary solid buttons, selected chips |
| yellow-saturated | "Expiring soon" tag (ink text, 11.9:1) |
| Display serif (Instrument Serif) | screen titles, onboarding hero, stats — 44/36/30sp phone scale |
| Inter 400/500/600 | everything below the display tier |
| radius 4/6/8/12/16 | tags / inputs / buttons / cards / panels — no pills |
| SunsetStripe | once per screen: onboarding, Home footer, Settings footer, Premium |
| PixelBlocks | onboarding and Premium only; assembles bottom-up, off when animations are disabled |
| Icons | outlined line icons only, no emoji |

Dark theme follows the system's dark tokens (#141414 ground, #261f14 warm surface, orange unchanged).

## 6. Phase plan

1. **Foundation** ✅ structure, theme, navigation, DB, repositories, settings, onboarding
2. **Core vault** ✅ receipts, warranties, subscriptions, links · ⏳ documents (moves to 5, needs encryption)
3. **Intelligence** ✅ search, reminders · ⏳ camera/photo picker, ML Kit on-device OCR with confirm-every-field UI, screenshot categoriser (MediaStore + user-approved `createDeleteRequest`)
4. **Monetisation** ✅ limits, entitlement layer, contextual prompt · ⏳ Play Billing, backend verification, restore
5. **Security** ⏳ Keystore-backed encrypted files, documents vault, biometric lock, timeout, FLAG_SECURE on sensitive screens, account/data export
6. **QA** ⏳ instrumented Room/migration tests, Compose UI tests, TalkBack pass, font scale 200%, rotation, offline, low storage
