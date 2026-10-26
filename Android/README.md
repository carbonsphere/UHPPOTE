# UHPPOTE Manager (Android)

An Android app for UHPPOTE / WeiGeng access controllers, built for touch screens in
portrait and landscape. It does what the [Windows app](../Windows/README.md) does:

- **On a phone:** a manager on the controller's Wi‑Fi opens a door with one tap, from
  the app or from a home-screen widget.
- **On a tablet** kept inside: add and delete cards, read events, check status and
  change controller settings.

It talks to the controllers directly over the local Wi‑Fi (UDP port 60000). There is no
server, no account and no internet connection involved.

## Install

1. On the phone or tablet, download `UHPPOTE-Manager.apk` from the
   [Releases page](https://github.com/carbonsphere/UHPPOTE/releases).
2. Open it. Android asks you to allow **Install unknown apps** for your browser or
   Files app the first time; allow it and go back.
3. Google Play Protect may warn that the app is unknown, because it is not from the
   Play Store. Tap **More details → Install anyway**.

To update, install a newer APK the same way. Your saved sites are kept.

Needs Android 8.0 or newer.

## First run

1. Connect the phone to the **controller's Wi‑Fi**.
2. Open the app and tap **Add controllers**. It searches the network; tick the
   controllers you want and save them to a new **site** (for example "Office").
   If a controller is on another subnet, type its IP address and search again.
3. Next time, the app picks the right site by itself: it checks which site has a
   controller on the network you are on, then which one answers, then searches.
   Tap the site name at the top to switch site or manage sites.

A site can hold several controllers. Name each controller and its doors under
**Settings → Doors**, so the buttons say "Front door" instead of "Door 1".

## Screens

| Screen | What you can do |
|---|---|
| **Doors** | A big **Open** button for every door at the site, with the door sensor (open/closed) and lock state, refreshed every 2 s. Long-press a door (or tap ⚙) for its lock mode and unlock time. The ⋮ menu has interlock and reader keypad settings. |
| **Status** | Door sensors, buttons and locks, the controller clock and the last event. Optional auto-refresh every 2 s. |
| **Cards** | The full card list (read slot by slot and remembered on the device), search by number, add / edit / delete a card with its validity dates and per-door access (no access, allowed, or a time profile 2–254). "Delete all cards" sits in a danger zone with a confirmation that names the controller. |
| **Events** | The last N events or only the unread ones, newest first, and "Mark all as read". Pull down to fetch again. |
| **Settings** | Controller clock (read, sync to this device), event listener ("Use this device"), door names and count, network settings (IP / mask / gateway, with a confirmation), keep screen on, the activity log (with raw packets), and the app version. |

Status, Cards, Events and Settings work on one controller at a time; pick it at the
top right when the site has more than one.

## Home-screen widget

1. Long-press an empty spot on the home screen → **Widgets** → **UHPPOTE Manager** →
   **Open door**, and drag it into place. It is 2×1 by default and can be resized.
2. Pick the door it should open.
3. Tap the widget to open the door. It does this in the background, without opening
   the app, and shows **Opening…**, then **Opened ✓** (with a short vibration) or
   **No reply – on the right Wi‑Fi?**.

If the controller has moved to a new IP address, the widget finds it with a quick
search and saves the new address for the app too. The widget only knows controllers
saved in the app; if one is removed, the widget says **Set up again in the app**.

## Wi‑Fi without internet

Controller Wi‑Fi usually has no internet, so Android may keep mobile data as the
default connection. The app sends its packets over Wi‑Fi anyway, so it keeps working
without turning mobile data off.

## Build

You need JDK 17 or newer and the Android SDK (platform 35). Open this `Android/`
folder in Android Studio, or from a terminal:

```bash
cd Android
./gradlew test             # unit tests
./gradlew assembleDebug    # app/build/outputs/apk/debug/app-debug.apk
```

`./gradlew assembleRelease -PappVersion=1.2.3` builds a release APK with
`versionName` 1.2.3 and `versionCode` 10203. It is signed only when a key is
configured, through the environment variables `ANDROID_KEYSTORE_PATH`,
`ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` and `ANDROID_KEY_PASSWORD`
(this is what the GitHub release workflow uses). Otherwise you get
`app-release-unsigned.apk`.

## Code

| Path | Contents |
|---|---|
| `protocol/` | Pure Kotlin/JVM port of the protocol: `Packet` (64-byte layout, BCD, little-endian), `Types` (parsed replies), `Controller` (one method per command, 2.5 s timeout, one request at a time), `search()`. Tests check the packet bytes against the PHP library. |
| `app/.../data/` | Saved sites and controllers (`AppState`, kept as JSON in DataStore) and the card list cache |
| `app/.../net/` | Wi‑Fi socket binding, subnet maths, the activity log |
| `app/.../domain/` | Plain logic with unit tests: choosing the site on launch, event ranges, the card list loader, the card form, the widget's open-and-retry |
| `app/.../ui/` | Jetpack Compose screens, one ViewModel per screen |
| `app/.../widget/` | The Glance home-screen widget and its setup screen |

## Not included yet

Time-profile editing, scheduled task lists, super passwords, and receiving pushed
events (the app polls status instead), as in the Windows app. The PHP scripts in the
parent folder still cover these.

## License

MIT, see [`../LICENSE`](../LICENSE).
