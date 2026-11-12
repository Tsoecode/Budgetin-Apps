# Budgetin - Android budgeting app (Kotlin, Jetpack Compose)

Course project for BIT219 Introduction to Mobile Apps. Budgetin tracks income and expenses, sets category limits and
savings goals, shows statistics, follows live currency rates, shows weekly finance news and savings tips, and can
import bank notification e-mails automatically. The look follows the "Budgetly" reference (peach header, round
white header buttons, dark pill buttons, floating glass navigation).

> The Kotlin package is still `com.budgetpulse.app`, so updating over an earlier install keeps all saved data
> (the database upgrades itself from v1 to v2). Only the visible name, logo and look changed.

---------------------------------------------------------------------------------------------------

## 1. Quick start

1. Install **Android Studio Ladybug (2024.2.1) or newer** (bundled JDK 17).
2. `File > Open` this folder and wait for the Gradle sync (internet needed the first time).
   Pinned versions: Gradle 8.9, AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.12.01, Room 2.6.1, Navigation 2.8.5.
3. Run on a phone emulator (e.g. *Pixel 8*), then on a tablet (*Pixel Tablet*) and rotate both.
   The *Resizable* emulator shows all three layouts (Compact / Medium / Expanded).
4. First launch: animated splash > **Get Started > name + e-mail > currency**.

Command line: `./gradlew assembleDebug` (APK in `app/build/outputs/apk/debug/`).

---------------------------------------------------------------------------------------------------

## 2. What changed in this revision

| # | Request | What was done | Where |
|---|---|---|---|
| 1 | Dark theme unreadable, more modern background | **Root cause:** nothing set a default content colour, so any `Text`/`Icon` outside a card was BLACK on dark. The theme now provides it. New graphite palette with orange accent, soft orange + indigo glows instead of a brown fade, hairline card borders. All pairs measured (section 8). | `ui/theme/*`, `ui/common/Glass.kt` (`appBackground`) |
| 2 | Bell, Home button, "Inflow" | Bell opens a **notification list**: one entry for every transaction added by hand or imported from e-mail. The badge counts unread and **disappears when the list is opened**. **Home button bug:** Navigation filed the page you left under Home's own id, and `restoreState` re-opened it. Home is now handled explicitly (`popBackStack` to Home). "Inflow" is now **Income** everywhere. | `ui/notifications/*`, `ui/navigation/BudgetPulseRoot.kt` |
| 3 | Rate wording | Rates always read as **"1 USD = 4.06 MYR"** (the stronger currency is the "1"), in the conversion dialog, in every Market row and under the converter. The Market **follows the currency chosen in Settings** automatically. Also fixed: amounts such as US$1.28 showed as "US$1" because the phone's rupiah locale has no decimals. | `domain/logic/RateDisplay.kt`, `util/Money.kt`, `ui/market/*` |
| 4 | Name and logo | Renamed **Budgetin**, new minimal logo (a bold "b" whose bowl is a coin ring) as app icon (adaptive + themed monochrome), in-app mark and splash. | `res/drawable/ic_logo_mark.xml`, `ic_launcher_*` |
| 5 | E-mail scan interval | Dropdown **Every 10 minutes / Every hour** (default: hour). WorkManager cannot do periodic jobs below 15 minutes, so each check schedules the next one; the chain cannot loop or double up. | `email/EmailSyncWorker.kt`, Settings |
| 6 | Edit account | Pencil on the profile card: edit name and e-mail (validated). | Settings |
| 7 | Demo data | "Load demo data" and its code removed. "Delete all transactions" stays. | Settings |
| 8 | Splash | Drifting gradient in the app colours with the logo in the centre; the system splash (Android 12+) uses the same colour. | `ui/splash/SplashScreen.kt` |
| 9 | One folder for images/icons | All 59 icons + logo + mascot + launcher art are vector files in **`app/src/main/res/drawable/`**. The big Material icon library was removed. (Android requires the launcher descriptor `ic_launcher.xml` in `mipmap-anydpi-v26`; it only points at drawables.) | `res/drawable/` |
| 10 | Do not change other features | Everything else is unchanged; the database upgrade keeps existing data. | - |
| 11 | Light on memory | See section 6. | - |

---------------------------------------------------------------------------------------------------

## 3. Where each assignment requirement lives

| Brief | What was built | Main files |
|---|---|---|
| 2.3.1a Compose + Material 3 | Every screen is Compose / M3 with custom light + dark schemes | `ui/theme/*`, `ui/**` |
| 2.3.1b Animation | Chart areas/bars grow on every change; progress bars animate from 0; nav pill, fades, animated splash | `ui/stats`, `ui/common`, `ui/splash` |
| 2.3.1c Light / dark theme | System / Light / Dark switch in DataStore | `ui/theme/Theme.kt` |
| 2.3.1d Accessibility | Contrast table (section 8), descriptions on every icon-only control and chart, range semantics on progress bars, `sp` text, 48 dp round buttons | all `ui/` |
| 2.3.2 Navigation | Type-safe routes with arguments, auth-driven start/redirects, Compact = glass bar, Medium = glass rail, Expanded = glass drawer, two-pane layouts, back button on every screen entered from another | `ui/navigation/*` |
| 2.3.3 Network | ExchangeRate-API (+ ECB/Frankfurter fallback) via Retrofit, Google News RSS via OkHttp, Coil images, coroutines, repositories, manual DI (`AppContainer`) | `data/remote`, `data/repository`, `di/` |
| 2.3.4 Persistence | Room (7 tables, migration v1 to v2), DataStore (theme, currency, target, login, e-mail settings) | `data/local`, `data/prefs` |
| 2.3.5 Debugging/testing | `android.util.Log` tags, 65 JVM unit tests, 39 instrumented tests | `util/AppLog.kt`, `app/src/test`, `app/src/androidTest` |

---------------------------------------------------------------------------------------------------

## 4. Features worth knowing

**Notifications (bell).** Added a transaction by hand, or the e-mail importer found one: a short entry appears
("Expense added - Lunch - MYR 12.50 - Food - Added manually - 5 min ago"). Tap an entry to open the transaction.
Opening the list clears the badge (new entries keep a dot until you leave). "Clear all" empties the list; the newest
100 are kept. Stored amounts follow currency conversions.

**Changing the main currency (Settings).** Fetches the live rate, shows `1 USD = 4.06 MYR`, and after **Convert**
changes - in one all-or-nothing database transaction - every transaction, account balance, category limit, goal and
notification amount, then the savings target and the currency. Offline = nothing changes. Amounts round to the new
currency's decimals; the same (today's) rate is applied to old data. Signing out and in with another currency converts
the same way. An e-mail in a different currency than yours is converted with its live rate (the original stays in
the note); without rates it waits for the next check instead of storing a wrong number.

**E-mail import.** Read-only IMAP, one transaction per e-mail (unique Message-ID), OTP/promo/statement mails ignored.
Gmail: turn on 2-Step Verification, create an *App password*, enter it in Settings, choose the interval (10 minutes or
1 hour), Save, Sync now. The password is encrypted with the Android Keystore. Doze mode may delay a check when the
phone is idle. Microsoft (Outlook/Hotmail) accounts normally reject app-password IMAP.

**Currency market.** Daily reference rates (not tick-by-tick trading prices), cached in Room, offline banner,
attribution "Rates By Exchange Rate API" in Settings.

**News and tips.** Google News RSS (last 7 days, topic rotates weekly) as a swipeable clickable carousel; the Bank Jago
article behind your share link is embedded, plus nine original tips.

---------------------------------------------------------------------------------------------------

## 5. Look and feel

* **Glass navigation.** The page is captured into a `GraphicsLayer`, drawn again behind the bar with a blur, then
  tinted, with a light rim and soft shadow (real backdrop blur on Android 12+).
* **Dark mode** is its own design (graphite + glows), not a darkened light mode.
* **Back button:** round, top-left, on every screen opened from another screen. The system back gesture works too.
* **Images and icons:** one folder, `res/drawable/` (59 `ic_*.xml` Material icons, `ic_logo_mark`, `img_mascot`,
  launcher art). Use them with `AppIcon(R.drawable.ic_x, ...)`.

---------------------------------------------------------------------------------------------------

## 6. Light on memory (under-spec phones)

* **Automatic device tier.** Phones with Android < 12, under ~3 GB RAM, a small app heap or the low-RAM flag run
  in *Lite*: no screen capture or blur (a plain frosted tint instead), news only scrolls by hand, and the embedded
  web page (a WebView costs ~100 MB) loads only when you tap **Show**. Capable phones get the full effects.
* **Small image caches:** memory cache 6-12 % of the app heap, 24 MB disk cache, RGB-565 for opaque pictures,
  640x360 news banners, no crossfades.
* **Lighter app:** the huge icon library is gone (plain vector files), mascot and logo are flat vectors,
  the splash animation is read only while drawing, release builds use R8 code + resource shrinking.
* **Cheap background work:** one short IMAP check per interval, only while online; stopped when the switch is off.
* **No** `largeHeap`, no always-on services, lists are lazy.

---------------------------------------------------------------------------------------------------

## 7. Tests, logging and verification

```
./gradlew testDebugUnitTest            # 65 JVM unit tests  (app/build/reports/tests/testDebugUnitTest/index.html)
./gradlew connectedDebugAndroidTest    # 39 instrumented tests (Compose UI + Room) on an emulator/device
```
Logcat: `adb logcat -s BP-Net BP-DB BP-State BP-Email BP-Nav`

**Verification status (honest).** The Android SDK is not available where this project was generated, so a real
Gradle build was **not** run. What was verified: the 65 unit tests were compiled with `kotlinc` and executed (65
passed, 0 failed) through a small JUnit-compatible shim; every file was parsed for syntax errors; custom checkers
confirmed that every library symbol is imported from its real package (this class of mistake caused the first build
error: `rememberSaveable` and `PullToRefreshBox` live in sub-packages), every project function call matches its
declaration, every `R.drawable.*` exists, every XML file is well-formed; and the Compose APIs used for the glass bar,
charts and navigation were checked against the AndroidX source. **Not verified:** the Gradle build itself, the 39
instrumented tests, the Room v1 to v2 migration on a real old database, and how the glass blur looks on a device.
Run the two commands above and send the first error lines if anything fails.

---------------------------------------------------------------------------------------------------

## 8. Accessibility - measured contrast (WCAG 2.1, from the actual theme colours)

| Theme | Pair | Colours | Ratio | Needed | Result |
|---|---|---|---|---|---|
| LIGHT | Body text on card | #1C1410 on #FFFFFF | 18.2:1 | 4.5:1 | Pass |
| LIGHT | Body text on background | #1C1410 on #FFF7F2 | 17.1:1 | 4.5:1 | Pass |
| LIGHT | Secondary text on card | #55483F on #FFFFFF | 8.8:1 | 4.5:1 | Pass |
| LIGHT | Button text on primary | #FFFFFF on #C44F0C | 4.7:1 | 4.5:1 | Pass |
| LIGHT | Primary accent text on card | #C44F0C on #FFFFFF | 4.7:1 | 4.5:1 | Pass |
| LIGHT | Dark pill button (onSecondary) | #FFFFFF on #1F1B18 | 17.1:1 | 4.5:1 | Pass |
| LIGHT | Text on primaryContainer | #3A1500 on #FFE0CC | 13.0:1 | 4.5:1 | Pass |
| LIGHT | Income green on card | #1B7F55 on #FFFFFF | 5.0:1 | 4.5:1 | Pass |
| LIGHT | Expense red on card | #BA1A1A on #FFFFFF | 6.5:1 | 4.5:1 | Pass |
| LIGHT | Input outline vs background (graphic) | #85766B on #FFF7F2 | 4.1:1 | 3.0:1 | Pass |
| DARK | Body text on card | #E9EAEE on #161A21 | 14.5:1 | 4.5:1 | Pass |
| DARK | Body text on page background | #E9EAEE on #0C0E13 | 16.1:1 | 4.5:1 | Pass |
| DARK | Secondary text on card | #B9BDC8 on #161A21 | 9.3:1 | 4.5:1 | Pass |
| DARK | Secondary text on page background | #B9BDC8 on #0C0E13 | 10.3:1 | 4.5:1 | Pass |
| DARK | Button text on primary | #3A1600 on #FFA566 | 8.4:1 | 4.5:1 | Pass |
| DARK | Primary accent text on card | #FFA566 on #161A21 | 9.0:1 | 4.5:1 | Pass |
| DARK | Light pill button (onSecondary) | #14161C on #F0F1F5 | 16.0:1 | 4.5:1 | Pass |
| DARK | Income green on card | #5BE0A3 on #161A21 | 10.5:1 | 4.5:1 | Pass |
| DARK | Expense red on card | #FF9C94 on #161A21 | 8.7:1 | 4.5:1 | Pass |
| DARK | Text on primaryContainer (insight card) | #FFDCC4 on #4A2A12 | 10.0:1 | 4.5:1 | Pass |
| DARK | Text on income container | #B8F5DA on #0F4A38 | 8.3:1 | 4.5:1 | Pass |
| DARK | Text on error container | #FFDAD6 on #5C1A17 | 10.1:1 | 4.5:1 | Pass |
| DARK | Card edge vs page background (graphic) | #2E3441 on #0C0E13 | 1.5:1 | 1.0:1 | Pass |
| DARK | Input outline vs card (graphic) | #8A90A0 on #161A21 | 5.5:1 | 3.0:1 | Pass |
| SPLASH | White logo on gradient (deep orange) | #FFFFFF on #9A3A07 | 7.0:1 | 3.0:1 | Pass |
| SPLASH | White logo on gradient (brand orange) | #FFFFFF on #C44F0C | 4.7:1 | 3.0:1 | Pass |
| SPLASH | White logo on gradient (bright orange) | #FFFFFF on #EC6D17 | 3.1:1 | 3.0:1 | Pass |

Also: all text in `sp`, icon buttons labelled, charts/banners have text alternatives, progress bars announce their
percentage, touch targets 48 dp, nothing is colour-only (amounts carry +/-, limits say "Over by ...").

## 9. Evidence checklist for the report

| Section | Capture |
|---|---|
| UI / Material | Home, History, Budgets, Stats in light **and** dark; Settings theme chips; splash (screen recording) |
| Navigation / layouts | Phone (glass bar), tablet portrait (rail), tablet landscape (drawer + list/detail), back buttons, Home button after Settings |
| Network | Market (rates + converter), Tips (article + news), conversion dialog, offline banner, Logcat `BP-Net` |
| Persistence | add/edit/delete, limits, goals, theme persisting after restart, notification list, Database Inspector |
| E-mail import | Settings card with interval dropdown, Sync now, imported transaction + its notification |
| Testing | both HTML test reports, Logcat per tag |

## 10. Known limitations

* The glass blur needs Android 12+; older phones use the opaque-tint fallback.
* Sign-in is local (profile stored on the device), there is no server.
* Receipt scanning and account-to-account transfers from the reference are not implemented.
* `Double` money is fine for coursework; use minor units/`BigDecimal` for a real ledger.
* Currency data is daily, not real-time; converting back and forth can differ by a rounding step.

## 11. Credits

Exchange rates: ExchangeRate-API (https://www.exchangerate-api.com), ECB data via Frankfurter. News: Google News RSS.
Flags: flagcdn.com. Savings article: Bank Jago. Banner pictures: picsum.photos. Icons: Material Icons (Google, Apache-2.0).
Libraries: Jetpack Compose, Room, DataStore, WorkManager, Navigation, Retrofit, OkHttp, kotlinx.serialization, Coil, Android Mail.
