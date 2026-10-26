# UHPPOTE Manager (Windows)

A native Windows desktop app for UHPPOTE / WeiGeng access controllers. It finds
controllers on the network, lets you connect to one, send it commands and watch its
status. It is a single `.exe`: no PHP, no runtime, no installer.

The protocol code in `uhppote/` is a Go port of the PHP library
(`../UHPPOTE.php`), checked against the packets the PHP library builds.

## Layout

```
┌ Search: [ blank = all networks, or an IP ] [Search]       Connected: 305419896 @ 192.168.1.50 ┐
├ Controllers found ─────────────┬ [Status] [Doors] [Cards] [Events] [Settings] ────────────────┤
│ ● 305419896  192.168.1.50 6.62 │                                                              │
│   305419897  192.168.1.51 6.62 │   (the selected tab's controls)                              │
│ [Connect] [Disconnect]         │                                                              │
├ Log ───────────────── [ ] Show raw packets (hex) ─────────────────────────────────── [Clear] ─┤
│ 10:15:30.120  ✔ Open door 1                                                                    │
└ status bar ────────────────────────────────────────────────────────────────────────────────────┘
```

1. **Search** runs at startup. With the box blank, it broadcasts on every network
   adapter of the PC. Type an IP address to search a specific controller, for example
   one on another subnet that broadcasts can't reach.
2. **Connect** (or double-click a row) checks that the controller answers, then
   enables the tabs.
3. The tabs:

| Tab | What you can do |
|---|---|
| **Status** | Door sensor, push button and lock relay for doors 1–4. Controller time, error state and the last event. *Auto-refresh every 2 s* for live monitoring. |
| **Doors** | Open a door. Read/save lock mode (normally open / normally closed / controlled) and unlock time. Set the interlock and enable reader keypads. |
| **Cards** | Card count. Look up, add/update or delete a card, with validity dates and per-door access (none / allowed / time profile 2–254). Delete all cards (asks for confirmation). |
| **Events** | Newest and "read" event index. Fetch the last N events or only unread ones. Mark all events as read. |
| **Settings** | Read the clock, or sync it to the PC. Get/set the event listener (with a *Use this PC* button). Change the controller's IP, mask and gateway (asks for confirmation, then searches again). |

Every action is logged at the bottom. Tick **Show raw packets** to see the 64-byte
UDP packets in hex.

## Build

Requires Go 1.24 or newer: https://go.dev/dl/

- **On Windows:** run `build.bat` in this folder.
- **From Linux/WSL/macOS:** run `./build.sh` in this folder.

Both run the protocol tests, then build `UHPPOTE-Manager.exe` in this folder. The
first build downloads the GUI library ([tailscale/walk](https://github.com/tailscale/walk));
later builds use Go's cache.

The Windows resources (manifest, icon, version info) are pre-generated in
`gui/rsrc_windows_*.syso`. Only regenerate them if you edit `gui/winres/`:

```
cd gui
go run github.com/tc-hib/go-winres@v0.3.3 make --arch amd64,arm64
```

## First run

- **Windows Firewall** will ask whether to allow the app on the network. Allow it
  on *Private* networks, or search replies will be blocked.
- The PC and the controller must be able to reach each other over **UDP port
  60000**. Broadcast search only works on the same network segment.

## Code

| Path | Contents |
|---|---|
| `uhppote/packet.go` | 64-byte packet layout and BCD/little-endian encoding |
| `uhppote/types.go` | `Device`, `Status`, `Event`, `Card`, `DoorControl`, `Listener` with reply parsing |
| `uhppote/client.go` | `Search()` (broadcast to all adapters) and `Controller`, one method per command, with 2.5 s timeouts |
| `uhppote/uhppote_test.go` | Packet bytes checked against the PHP library's output, plus a loopback fake controller |
| `gui/main.go` | Main window, search and connect |
| `gui/pages.go` | The five tabs and their actions |
| `gui/app.go` | Background-task runner, log and table model |

All network calls run off the UI thread. Requests to one controller go out one at a
time.

## Not included yet

Time-profile editing (`set_timeAccess` / `get_timeAccess`), scheduled task lists,
super passwords, and receiving pushed events (the app polls status instead).
The PHP scripts in the parent folder still cover these.

## License

MIT, see [`../LICENSE`](../LICENSE).
