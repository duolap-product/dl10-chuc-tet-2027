# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

"Chúc Tết 2027" (`online.duolap.tetviet`): a single-module (`:app`) Android app for Vietnamese Lunar New Year: countdown, greeting-card maker, Tết photos/music/customs, a wishing tree, and mini-games (Bầu Cua, Lucky Wheel, Ô Ăn Quan, Đập Chuột). Kotlin, XML layouts with ViewBinding (no Compose), Hilt, Room, Firebase, AdMob, and Play Billing. Code comments, log messages and default strings are in Vietnamese.

## Commands

```bash
./gradlew assembleDebug                 # debug build
./gradlew assembleRelease               # minified + shrunk, signed via keystore.properties
./gradlew testDebugUnitTest             # JVM unit tests
./gradlew testDebugUnitTest --tests "online.duolap.tetviet.game.oanquan.OAnQuanEngineTest"
./gradlew testDebugUnitTest --tests "*.MinimaxBotTest.someTestName"
./gradlew connectedDebugAndroidTest     # instrumented tests (device/emulator required)
./gradlew lint
```

- `app/build.gradle.kts` reads `keystore.properties` at configuration time to build the `release` signing config. If that file is missing, Gradle configuration fails for **every** task, including debug builds.
- Toolchain: AGP 9, Kotlin 2.2 with KSP (Room and Hilt compilers), Java 21. Dependency versions live in `gradle/libs.versions.toml`.
- Release builds also need real AdMob IDs as Gradle properties (`ADMOB_APP_ID`, `ADMOB_REWARDED_ID`, `ADMOB_INTERSTITIAL_ID`, `ADMOB_BANNER_ID`, `ADMOB_APP_OPEN_ID`). `preReleaseBuild` fails if they are missing or still Google test IDs. Debug builds always use test IDs (via `BuildConfig` and the `admobAppId` manifest placeholder).
- Bump `versionCode`/`versionName` in `app/build.gradle.kts` for releases (commits are titled "Release x.y.z-beta").

## Architecture

**Startup (`TetVietApplication`, `@HiltAndroidApp`)**: initializes Crashlytics/Analytics, the Room DB, `AdManager`, `BillingManager` and `TetMusicManager`, then launches a background Firestore sync for the card-maker collections. It also counts started activities so that background music pauses and resumes with app foreground state.

**DI (`di/`)**: everything is a `SingletonComponent` provider. `DatabaseModule` (DB, DAOs, `FirestoreSyncManager`), `MonetizationModule` (`VipPreferences`, `BillingManager`, `AdManager`) and `UpdateModule` (Play in-app update). Activities are `@AndroidEntryPoint`. Only `CardMakerActivity` and `VipActivity` use ViewModels; the other screens hold their logic in the Activity.

**Data layer**:
- Room `AppDatabase` (`tet_viet_local.db`) uses `fallbackToDestructiveMigration()` and `exportSchema = false`. Adding an entity or changing a schema means bumping `version`, and existing local data gets wiped. That is acceptable only because the content is re-synced or re-seeded.
- `FirestoreSyncManager` pulls the Firestore collections `tao_thiep_hinh_nen` (card backgrounds/stickers) and `tao_thiep_mau_sac` (card colors) into Room. Syncs are throttled to **once per day per collection** through `AppSyncPreferences` (to save Firestore quota). `forceRefresh` bypasses the throttle. `data_firestore/` holds seed JSON for these collections.
- Repositories (`data/repository/`) wrap the DAOs. `CardMakerRepository` falls back to `CardMakerDefaults` when no data has synced yet. `WishingTreeRepository` seeds initial wishes and exposes `Flow`s.
- Small per-feature state goes in SharedPreferences wrappers (`*Preferences.kt`: `VipPreferences`, `BauCuaPreferences`, game preferences).

**Monetization**: `VipPreferences` is the single source of truth (StateFlows `isVip` and `unlockedFeatures`). VIP status comes from `BillingManager` purchases/restore. Non-VIP users can unlock a feature temporarily by watching a rewarded ad (`AdManager` + `UnlockVipFeatureDialog` → `unlockFeatureTemporarily(featureId)`). Gate features with `isFeatureUnlocked(featureId)`. `AdManager` skips ads for VIP users.

**Ads (`ads/`)**: AdMob is initialized from `MainActivity` only after UMP consent (`AdConsentManager`), not in `Application`. Every `show*Ad`/`attachBanner` call takes an `AdPlacement` (used for Analytics events `ad_shown`/`ad_skipped`/`ad_reward_earned`/`ad_load_failed`). Interstitials are gated by `AdPolicy` (first session ad-free, 90s interval, 2 min after a rewarded ad, caps of 4 per session and 12 per day); `TetVietApplication` feeds it foreground/background events. `showInterstitialAd`'s `onDismissed` always fires, even when the ad is skipped, so put the user's next action there. Show interstitials only at natural breaks (after a dialog closes), never over a result sheet. App Open (`onAppForeground`, called from `TetVietApplication` when the first activity resumes after the app comes to the foreground; calling `show()` in `onStart` fails because the SDK still sees the app as backgrounded, and on Samsung even `onResume` can be too early, so `AdManager` waits up to ~1s for the process to reach foreground importance) only shows on a warm return after at least 30s in the background, at most once per 6h, never in the first session, and shares the 90s full-screen spacing with interstitials. Call `AppOpenGate.skipNextForeground()` before sending the user out on purpose (share, Play billing/update, external links). Widget and notification intents carry `AppOpenGate.EXTRA_SKIP_APP_OPEN`. Banners (`attachBanner` into a `bannerAdContainer` FrameLayout at the bottom of the layout) are only on content screens (Customs, Photos, Music), never on games, the card editor or the wheel.

**Home-screen widgets (`widget/`)**: there are two widgets, both extending `TetWidgetProviderBase`, which handles the midnight RTC refresh alarm (one per provider class), time/locale broadcasts and `requestPin`. Use only RemoteViews-safe views in their layouts.
- `TetClassicWidgetProvider` (`widget_tet_classic`): pill frame with gold clouds, showing calendar days until Tết (`calendarDaysUntilTet`).
- `TetLixiWidgetProvider` (`widget_tet_lixi` / `_large`): full days plus a `Chronometer` counting down to Vietnam midnight, a progress bar, the lunar date (`LunarCalendar`) and, in the tall layout, a daily wish. On Android 12+ both layouts go in one size-mapped `RemoteViews` because Samsung's reported sizes are unreliable.

Settings → "Thêm widget" opens `dialog_widget_picker`, which previews each widget by applying its real `createViews()` RemoteViews. `TetCountdown` is the single source of the Tết dates (`MainActivity` uses it too), so change them only there. Renaming a provider class removes users' existing widget instances.

**Games (`game/<name>/{engine,ai,ui,online}`)**: game rules are pure Kotlin `object` engines (`OAnQuanEngine`, `WhackMoleEngine`) that operate on mutable state classes and return step lists for the UI to animate. They have no Android dependencies, which is what the unit tests in `app/src/test/.../game/` cover, so new game logic belongs in the engine. Ô Ăn Quan also has a minimax bot (`ai/`), a custom-drawn `OAnQuanBoardView`, and online multiplayer through Firestore rooms (`OAnQuanOnlineManager`, which maps `OAnQuanRoomData` ↔ `OAnQuanBoardState`).

**UI conventions**: custom views live in `views/`. Adapters are split between `adapter/` and the newer `adapters/` package. Strings come in Vietnamese (`values/`, the default) and English (`values-en/`), so add every new string to both. Fonts must cover Vietnamese diacritics. Use `arimo_bold`/`arimo_regular` (metric-compatible replacements for Liberation Sans), `lobster_regular` and `pacifico_regular`. The old `liberationsans_*`/`lobstertwo_*` files are kept in `res/font` but miss 48–54 Vietnamese glyphs, so don't use them. Check a new font's cmap before adding it. OFL licenses are in `assets/licenses/`. VIP-only card fonts live in `VipCardFontManager` (`CardMakerModels.kt`); locked ones open `UnlockVipFeatureDialog.newVipOnlyInstance`, which has no rewarded-ad unlock. Analytics and Crashlytics go through the `AppAnalytics` / `AppCrashlytics` wrappers.

**R8**: release builds are minified. `app/proguard-rules.pro` keeps `data.model`, `data.local`, `data.sync`, `views`, and the `game.oanquan`/`game.dapchuot` packages, because Room and Firestore depend on reflection. A new package with Firestore-mapped or reflectively used classes needs a keep rule.
