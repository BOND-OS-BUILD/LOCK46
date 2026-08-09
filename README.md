# LOCK46

**On duty. Off distractions.**

LOCK46 is an Android app that turns a personal phone into an essentials-only device for
the length of a duty period. The user starts the period, chooses how long it lasts, and
decides which apps stay available. Anything not explicitly approved is blocked the moment
it is opened. When the timer expires the phone returns to normal on its own.

| | |
| --- | --- |
| Version | `1.0.1` |
| Application ID | `com.lock46.app` |
| APK | `LOCK46-v1.apk` |
| Min / target SDK | 26 (Android 8.0) / 35 (Android 15) |
| Network | None. `android.permission.INTERNET` is not declared. |

---

## V1 features

- **Duty Mode** — presets of 30 min / 1 h / 2 h / 4 h / 8 h, plus a custom duration from
  1 minute to 24 hours. Live countdown and exact end time. State is persistent and duty
  ends automatically when the timer expires.
- **Essential app whitelist** — a picker over installed launchable apps showing icon and
  name. Default is **block**; only explicitly approved apps stay available. Saved locally.
- **Real app blocking** — enforcement happens when a restricted app is actually opened,
  not merely inside a LOCK46 launcher screen. A full-screen LOCK46 overlay appears with
  the remaining time and a Return Home button.
- **Installs like a normal app** — LOCK46 deliberately ships **no accessibility service**.
  Google Play Protect hard-blocks the installation of any sideloaded app that declares
  one, so an accessibility-based build would be uninstallable for the people it is for.
- **Phone access** — the dialer is always allowed, whether or not it is in the whitelist,
  so emergency calling is never intentionally blocked. Call content is never intercepted
  or collected.
- **Protected override** — ending duty early requires a locally configured admin PIN,
  stored as a salted PBKDF2-HMAC-SHA256 hash with attempt throttling.
- **Reboot persistence** — duty state survives a restart, including before the first
  unlock, and enforcement services restart automatically.
- **Offline-first** — no server, no account, no cloud, no API, no analytics, no location.

---

## Architecture

Native Android: Kotlin, Gradle with a version catalog, AndroidX, Jetpack Compose with
Material 3. One Gradle module, `:android`.

```
com.lock46.app
├── Lock46App.kt           Application + Graph (hand-rolled singleton container)
├── BuildConfigInfo.kt     Version / application id, asserted against Gradle by tests
│
├── core/                  Pure logic. No Android imports except SharedPreferencesStore.
│   ├── KeyValueStore.kt       Persistence interface + in-memory implementation
│   ├── SharedPreferencesStore.kt  Device-protected, synchronous commits
│   ├── DutySession.kt         DutySession, DutyStatus, DutyClock (time arithmetic)
│   ├── DutyRepository.kt      Duty state, persistence, expiry, clock reconciliation
│   ├── DutyDuration.kt        Presets and duration validation
│   ├── AccessPolicy.kt        The allow/block decision
│   ├── WhitelistRepository.kt Approved packages
│   ├── PinHasher.kt           PBKDF2 hashing, PIN policy
│   ├── PinRepository.kt       PIN storage + attempt throttling
│   └── SettingsRepository.kt  Onboarding flag, default duration
│
├── enforce/               Everything that makes a block actually happen.
│   ├── ForegroundAppMonitor.kt        UsageStats foreground detection
│   ├── Enforcer.kt                    Single decision point
│   ├── BlockOverlay.kt                The blocking screen (WindowManager overlay)
│   ├── DutyService.kt                 Foreground service, clock tick, notification
│   ├── DutyController.kt              start / end / restore orchestration
│   ├── BootReceiver.kt                Boot + clock-change restore
│   └── DutyExpiryReceiver.kt          Backstop expiry alarm
│
├── ui/                    Compose screens. Not on the enforcement path.
│   ├── MainActivity.kt        The only Activity
│   ├── OnboardingScreen.kt    Five-step first-run setup
│   ├── HomeScreen.kt          Status + Start Duty / End Duty
│   ├── AppPickerScreen.kt     Essential apps
│   ├── SettingsScreen.kt      Setup status, PIN, privacy, about
│   ├── DurationPickerDialog.kt / PinDialog.kt / ChangePinDialog.kt / Components.kt
│   └── theme/Theme.kt
│
└── util/
    ├── Permissions.kt     Every permission, its reason, and how to check it
    ├── InstalledApps.kt   App listing + always-allowed resolution
    └── TimeFormat.kt      Countdown formatting
```

**Design notes**

- `core/` has no Android dependencies apart from the SharedPreferences adapter, which is
  why the duty lifecycle, PIN and policy logic are covered by fast JVM unit tests with no
  Robolectric and no device.
- Duty state is stored as **wall-clock** start/end times rather than `elapsedRealtime`,
  because it must survive a reboot. `DutyClock.reconcile` compensates for the clock being
  moved backwards so a clock change cannot buy duty-free time.
- Every foreground signal funnels into `Enforcer.onForegroundPackage`, so the allow/block
  rule exists in exactly one place.
- Persistence uses **device-protected storage** with synchronous `commit()`, so the boot
  receiver can read duty state before first unlock and a process kill cannot lose a write.

---

## How the blocking mechanism works

1. **Detect.** While a duty period is running, `DutyService` polls `UsageStatsManager`
   every 600 ms for the most recent foreground event and reads nothing but the package
   name. Polling stops entirely in Free Mode.
2. **Decide.** `AccessPolicy.decide` returns ALLOW or BLOCK from the duty state, the
   user's whitelist, and a runtime-resolved always-allowed set (LOCK46 itself, the
   launcher, system UI, enabled keyboards, the dialer).
3. **Block.** `BlockOverlay` adds a full-screen `TYPE_APPLICATION_OVERLAY` window on top
   of the offending app, showing the message, the blocked app, the remaining time and a
   Return Home button. BACK is swallowed. There is deliberately no "disable" control on
   this screen.
4. **Fallback.** Without the overlay permission LOCK46 can only send the user back to the
   home screen. The app says so, in those words, on its setup screen.

### Why polling rather than an accessibility service

An `AccessibilityService` reports a window change instantly and would be the obvious
choice. It is not used, because Google Play Protect **hard-blocks the installation** of any
sideloaded app whose manifest declares `BIND_ACCESSIBILITY_SERVICE` — a block with no
"install anyway" option, currently enforced in India among other regions. An app nobody can
install enforces nothing.

The trade is detection latency: sub-second instead of instant. The blocking overlay covers
the app the moment the poll fires, so in practice the user sees LOCK46 rather than a usable
app. `RepositoryHygieneTest` fails the build if an accessibility service is ever
re-introduced.

---

## Permissions

Every permission is listed in-app with the reason below. Nothing is requested
speculatively.

| Permission | Type | Why |
| --- | --- | --- |
| `PACKAGE_USAGE_STATS` | Special access | Detect which app came to the foreground. Reads package names only. |
| `SYSTEM_ALERT_WINDOW` | User-granted | Draw the blocking screen over a blocked app. Also the documented route to putting UI in front of the user from a service on Android 10+. |
| `FOREGROUND_SERVICE` + `_SPECIAL_USE` | Normal | Keep the enforcement service alive for the duty period. |
| `POST_NOTIFICATIONS` | Runtime | The ongoing Duty Mode notification Android requires for that service. |
| `RECEIVE_BOOT_COMPLETED` | Normal | Restore duty state after a reboot. |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | User-granted | Stop aggressive battery management killing the service mid-duty. |

**Deliberately absent:** `INTERNET`, location, contacts, SMS, call log, microphone,
camera, and `QUERY_ALL_PACKAGES`. Package visibility uses a narrow `<queries>` block.

---

## Installation

1. Download `LOCK46-v1.apk` from the website or the GitHub release.
2. Open it and allow installation from that source when Android asks.
3. Launch LOCK46 and complete the five-step setup.

Or via adb:

```bash
adb install -r LOCK46-v1.apk
```

## Starting a duty period

1. Open LOCK46. The status panel reads **FREE MODE — Ready for duty.**
2. Tap **START DUTY**.
3. Pick a preset or enter a custom duration, then confirm.
4. The panel switches to **DUTY MODE ACTIVE** with the countdown and end time.

To end early, tap **END DUTY** and enter the admin PIN. Otherwise the period ends by
itself.

## Configuring essential apps

**ESSENTIAL APPS** from the main screen or Settings. Toggle on the apps that must remain
available and press **SAVE**; **CANCEL** leaves the enforced list untouched. Pinned rows
at the top (home screen, keyboard, dialer) are always allowed and cannot be switched off.

---

## Known Android limitations

Being straight about this matters more than the marketing:

- **This is not Device Owner enforcement.** LOCK46 V1 is a normal installed app. A
  sideloaded APK cannot obtain unlimited control over a personal Android phone.
- **It can be disabled by someone holding the unlocked phone.** Revoking usage access,
  booting into safe mode, or uninstalling LOCK46 all defeat it. The admin PIN prevents
  *casual* early exit. It is not tamper resistance.
- **Detection is sub-second, not instant.** A blocked app is visible for a fraction of a
  second before the overlay covers it. Removing that gap would require an accessibility
  service, which would make the app uninstallable — see above.
- **The PIN keyspace is small.** PBKDF2 with 210 000 iterations and a random salt raises
  the cost of attacking the stored hash, and attempts are throttled with escalating
  lockouts, but 4–8 digits is 4–8 digits.
- **OEM battery management varies.** Some vendors kill background services aggressively.
  The battery-optimisation exemption helps; it is not a guarantee.
- **Moving the clock forward ends duty early.** Backwards changes are compensated;
  forward jumps are not, because treating them as tampering would let an honest timezone
  or NTP correction extend a duty period.
- **Overlays cannot cover everything.** The status bar, the navigation bar, and certain
  secure system surfaces stay visible above the blocking screen.
- **Blocking is per-app, not per-content.** LOCK46 blocks an application; it has no view
  of what is inside one, by design.

---

## Build instructions

Requirements: JDK 17+, Android SDK with platform 35 and build-tools 35, `ANDROID_HOME`
set (or `sdk.dir` in `local.properties`).

```bash
./gradlew clean
./gradlew :android:testDebugUnitTest     # 83 unit tests
./gradlew :android:assembleDebug
```

Output: `android/build/outputs/apk/debug/android-debug.apk`

To produce the release-named artifact locally:

```bash
./gradlew :android:packageV1Apk
# → android/build/outputs/lock46/LOCK46-v1.apk
```

### Tests

Plain JVM unit tests, no emulator required:

- duty starts, expires, and reports remaining time correctly
- duty survives process restart and reboot state restoration
- a duty period that elapsed while the device was off comes back as Free Mode
- clock-change reconciliation, including a backwards clock change
- allowed / blocked app decisions, including the dialer and launcher never being blocked
- whitelist persistence, replacement and reload
- PIN verification, wrong-PIN rejection, lockout escalation, no plaintext in storage
- invalid durations rejected at every layer
- repository hygiene: no invalid package name, no accessibility service, one manifest,
  one Activity, no network permission, build identity matches Gradle

---

## APK location

| Where | Path |
| --- | --- |
| **Served by the website** | **`/LOCK46-v1.apk`** — committed at the repository root |
| Local build | `android/build/outputs/apk/debug/android-debug.apk` |
| Local, release-named | `android/build/outputs/lock46/LOCK46-v1.apk` |
| CI artifact | `LOCK46-v1-apk` on the workflow run |
| Release asset (mirror) | `https://github.com/BOND-OS-BUILD/LOCK46/releases/download/v1-latest/LOCK46-v1.apk` |

### Why the APK is committed

The download button links to `/LOCK46-v1.apk` — a static file on our own domain. Clicking
it hands the browser the binary directly: no github.com, no releases page, no Actions run,
no redirect. `vercel.json` sets `Content-Type: application/vnd.android.package-archive` and
a `Content-Disposition: attachment` for that path.

That makes the root APK a build output which has to be in version control, so `.gitignore`
excludes `*.apk` with a single deliberate exception for it. After changing app code:

```bash
./gradlew :android:refreshSiteApk   # rebuild and copy to the repository root
git add -f LOCK46-v1.apk
```

CI does this automatically on `main`. It compares `classes.dex` + `resources.arsc` between
the freshly built APK and the committed one — not the whole file, because zip timestamps
and the debug signature differ on every build while d8 output does not — and commits a
refresh only when the app has genuinely changed. Every run, including pull requests, fails
if the root APK is missing or is not a valid APK.

The GitHub release at the `v1-latest` tag is kept as a mirror for people who prefer it.

## CI

`.github/workflows/release.yml` is the only workflow. On a push to `main` it checks out,
sets up JDK 17 / Gradle / the Android SDK, runs `clean`, runs the unit tests, assembles
the debug APK, then **verifies the APK exists**, is larger than 1 MB, and contains an
`AndroidManifest.xml` before renaming it to `LOCK46-v1.apk`. It uploads the artifact,
publishes it to the moving `v1-latest` tag, and finally re-downloads the public URL to
confirm the asset actually serves a valid APK. If the APK is missing or malformed the
workflow fails and no release is published.

## Website

The static landing page lives at the repository root (`index.html`, `style.css`,
`vercel.json`) and deploys on Vercel with no build step.

---

## V2 roadmap

- **Android Enterprise / Device Owner** — real lock-task enforcement on a fully managed or
  work-profile device, which is the only way to make this genuinely tamper resistant. The
  V1 architecture keeps the decision (`AccessPolicy`) separate from the mechanism
  (`Enforcer`) so a device-policy mechanism can be added without rewriting the logic.
- Duty roster integration and scheduled/automatic duty periods.
- Remote configuration of whitelists by an authorised administrator.
- Commander dashboard and multi-device oversight.
- Per-app time budgets rather than a binary allow/block.
- Signed release builds and Play/private-channel distribution.

---

LOCK46 V1.0.1 · `com.lock46.app`
