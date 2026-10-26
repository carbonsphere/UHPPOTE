# UHPPOTE PHP Controller Library — Technical Documentation

A PHP library and set of scripts for talking directly to **UHPPOTE / WeiGeng (微耕)
Wiegand access-control boards** over the LAN, without the vendor's Windows software.
It builds the 64-byte UDP command packets the board understands and decodes the
replies into PHP arrays/values.

- Target firmware: **V6.56+**
- Transport: **UDP, controller port 60000**
- Language: plain PHP (no framework, no Composer), requires the **`sockets`** extension
- Upstream: https://github.com/carbonsphere/UHPPOTE
- Windows desktop app: [`Windows/`](Windows/README.md)

---

## 1. Project layout

| File | Purpose |
|---|---|
| `UHPPOTE.php` | The library: class `uhppote`. Builds command packets (`getCmdHex`) and parses replies (`procCmd`). Does **no** networking itself. |
| `sendCommand.php` | Command-line tool: `php -f sendCommand.php <ip> <serial> <command> [options]`. Covers most commands, with per-command help. |
| `search.php` | Broadcasts a `search` to 255.255.255.255:60000 and prints the first board's IP / mask / gateway / MAC / firmware / serial. **Start here.** |
| `example.php` | Minimal example: reads the board's remote-monitor IP/port (`get_ripp`). |
| `monitor.php` | Points the board's event stream at this machine (`set_ripp`), then loops forever printing swipe/door events. |
| `getRecordExample.php` | Reads the record index, fetches the latest swipe record, then walks back through older records. |
| `add-user-example.php` | Adds a card (`add_auth`) with a validity period and per-door access rules. |
| `del-user-example.php` | Deletes a card (`del_auth`), then reads recent records. |
| `timer-example.php` | Creates a time-access profile (`set_timeAccess`). |
| `set-super-password-example.php` | Sets/clears the four keypad "super passwords" for a door. |
| `taskList-example.php` | Scheduled tasks (e.g. "unlock door 1 at 06:30 daily"): clear → add → save. |
| `cmd-chk.php` | Offline scratch script: prints generated packets without sending them. Handy for checking packet bytes. |

Every example script repeats the same two helpers, `createSocket()` and
`getReturnPacket()`, rather than sharing them. `createSocket()` sets a 3-second
receive timeout, so a wrong IP or serial ends the script instead of hanging.

---

## 2. How it works

```
 your script                         UHPPOTE.php                     controller board
 ───────────                         ───────────                     ────────────────
 $u = new uhppote();
 $u->setSn("11223344");
 $hex = $u->getCmdHex('open_door', null, ['door'=>1]);   ──► 128-char hex string
 socket_sendto(hex2bin($hex)) ─────────────── UDP :60000 ──────────────────►
 socket_recv($reply) ◄──────────────────────── 64-byte reply ──────────────
 $u->procCmd(bin2hex($reply))  ──► true / array / int
```

The library only converts between **hex strings** and meaning. Your script owns the
socket, so you always `hex2bin()` before sending and `bin2hex()` before parsing.

### Typical workflow

1. Run `php search.php` on the same subnet as the board. Note the `ip` and `sn`.
2. Put the serial in `setSn(...)` and the IP in `$ip` of the script you want to run,
   or pass them to `sendCommand.php`.
3. Send commands. Most write commands return `true`/`false`. `getCmdHex()` returns
   `false` for an unknown command or an invalid door number.

### About the serial number

`setSn()` takes **8 hex digits** and byte-reverses them into the packet. The source
comments say it is usually the last 4 bytes of the board's MAC address
(`00:11:22:33:44:55` → `"22334455"`). It is **not** the number printed on the board.
The value `search` returns as `sn` is in the right form to pass back to `setSn()`.

---

## 3. Packet format

All requests are 64 bytes (128 hex chars). `padCmdPackets()` pads with `00`.

| Offset | Size | Content |
|---|---|---|
| 0 | 1 | Type, always `0x17` |
| 1 | 1 | Command code (table in §4) |
| 2 | 2 | `00 00` |
| 4 | 4 | Board serial, little-endian (omitted for `search`) |
| 8 | … | Command-specific payload |
| … | … | `00` padding up to 64 bytes |

Replies use the same header (type, cmd, 2 bytes, serial), followed by the result.
`procCmd()` switches on the reply's command byte.

### Encoding conventions

| Data | Encoding | Example |
|---|---|---|
| Card ID | decimal → uint32 **little-endian** | `10012345` → `B9C69800` |
| Serial / record index / port / super-password | uint32 / uint16 little-endian | port 60002 → `62EA` |
| IP address | 4 bytes, network order | `192.168.1.2` → `C0A80102` |
| Dates | **BCD**, written as the literal digits | `"20190522"` → `20 19 05 22` |
| Times | BCD `HHmm` | `"1630"` |
| Flags / door numbers / rule indexes | 1 byte, passed as 2-char hex strings | `'01'`, `'02'` |
| Confirm magic | `55 AA AA 55` | appended to `set_ip`, `del_auth_all`, `set_recordIndex`, `del_task_list`, `sav_task_list` |

Most `$param` values are pasted into the packet **as strings**. Always pass two-digit
strings (`'01'`, not `1`) unless the table below says otherwise.

---

## 4. Command reference

`getCmdHex($cmd, $dt = null, $param = null)`: the second argument `$dt` does
different jobs depending on the command: a `DateTime` for `set_time`, a record index
for `set_recordIndex` / `get_records`, and unused for everything else.

| Command | Code | `$param` / `$dt` | `procCmd()` returns |
|---|---|---|---|
| `search` | 0x94 | none (no serial needed) | `['cmd','ip','mask','gate','mac','ver','date','sn']` |
| `set_ip` | 0x96 | `ip`, `mask`, `gate` (dotted strings) | `[]` |
| `dev_status` | 0x20 | none | big array: last record (`index`, `cardid`, `swipeymdhms`, `swipereason`, …), `door1-4stat`, `door1-4button`, `systemstat` (`00` = OK), `systemtime`, `systemdate`, `battery`, `fire`, … |
| `get_time` | 0x32 | none | `['systemymdhms' => 'YYYY-MM-DD HH:MM:SS']` |
| `set_time` | 0x30 | `$dt` = `DateTime` | `['year','month','day','hour','minute','second']` |
| `open_door` | 0x40 | `door` 1–4, int or string (`3` or `'03'`). Default 1 | `bool` |
| `door_delay_get` | 0x82 | `door` 1–4 | raw 3-byte hex: door, mode, seconds |
| `door_delay` | 0x80 | `door` 1–4, `seconds` (int), optional `mode` (`'01'` open, `'02'` closed, `'03'` controlled, default) | raw 3-byte hex |
| `get_ripp` | 0x92 | none | `['ip','port','second']` |
| `set_ripp` | 0x90 | `ip`, `port` (int), `sec` (int; 0 = events only, no periodic status) | `bool` |
| `get_auth_rec` | 0x58 | none | `int`, number of cards stored |
| `get_auth` | 0x5A | `cardid` (decimal) | `['cardid','begymd','endymd']` or `false` if not found or deleted |
| `userid` | 0x5C | `userid` (int memory slot) | same as `get_auth` |
| `add_auth` | 0x50 | `cardid`, `beg`, `end` (`YYYYMMDD`), `ta1`–`ta4` per-door rule: `'00'` deny, `'01'` allow, `'02'`–`'FE'` time-profile index. Default `'01'` | `bool` |
| `del_auth` | 0x52 | `cardid` | `bool` |
| `del_auth_all` | 0x54 | none (sends confirm magic) | `bool` |
| `get_recordIndex` | 0xB4 | none | `['recordIndex' => int]`, the "already read" pointer |
| `set_recordIndex` | 0xB2 | `$dt` = index (int) | `bool` |
| `get_records` | 0xB0 | `$dt` = index; **fetches record `$dt + 1`**. Pass `0xfffffffe` to get the newest record | `['Index','Type','Access','Door','DoorStat','CardId','swipeymdhms','rType']` |
| `set_timeAccess` | 0x88 | time profile, see §5 | `[]` (prints "Timer set success" or an error) |
| `get_timeAccess` | 0x98 | `index` `'02'`–`'FF'` | same keys as `set_timeAccess` input |
| `set_superPass` | 0x8C | `doorIndex` 1–4, `spassword1`–`4` (ints, 0 = clear) | `bool` |
| `keypad_switch` | 0xA4 | `pad1`–`pad4`: `'00'` off, `'01'` on | `bool` |
| `interlock` | 0xA2 | `interlock`: `'00'` none, `'01'` doors 1+2, `'02'` 3+4, `'03'` (1,2)+(3,4), `'04'` 1+2+3, `'08'` all four | `bool` |
| `get_alarm_state` | 0xC2 | none | `['raw' => hex]`, payload after the serial (layout undocumented) |
| `reset_alarm` | 0xC0 | none | `bool` |
| `del_task_list` | 0xA6 | none (sends confirm magic) | `bool` |
| `add_task_list` | 0xA8 | task, see §6 | `bool` |
| `sav_task_list` | 0xAC | none (sends confirm magic) | `bool` |

### Other public methods

- `setSn(string $hex8)` / `getSn()`: set or read the board serial (stored byte-reversed).
  Building a command without a serial throws an `Exception`.
- `setDebug(bool $on)`: turn the library's debug output on (default) or off.
- `printCMD(string $hex)`: prints a packet as space-separated bytes.
- `ConvertDateByteToDateTime(string $hex8)`: decodes the packed 4-byte date/time
  format (7-bit year since 2000, 4-bit month, 5-bit day, then H/M/S÷2) into a
  `DateTime`. Nothing else in the codebase calls it.

---

## 5. Time-access profiles (`set_timeAccess`)

A profile (index `'02'`–`'FF'`) limits when a card may open a door. You attach it to a
card through `ta1`–`ta4` in `add_auth`.

```php
[
  'index' => '02',
  'beg' => '20190524', 'end' => '20291231',          // active date range
  'w1'=>'01','w2'=>'01','w3'=>'01','w4'=>'01',       // Mon..Thu  ('01' allowed)
  'w5'=>'01','w6'=>'00','w7'=>'00',                  // Fri..Sun
  'time1beg'=>'0800','time1end'=>'1200',             // up to three time windows
  'time2beg'=>'1300','time2end'=>'1800',
  'time3beg'=>'0000','time3end'=>'0000',
  'countType'  => '00',   // '00' total count, '01' independent count
  'countDay'   => '00',   // max uses per day   ('00' = unlimited)
  'countMonth' => '00',   // max uses per month
  'countZone1' => '00', 'countZone2' => '00', 'countZone3' => '00',
  'weekend'    => '00',   // '00' allowed on weekends, '01' denied
]
```

All keys are required. The library does not fill in defaults here.

---

## 6. Scheduled task list

Tasks make the board act on its own at a set time. The device **replaces the whole
list on save**, so it is always a three-step job:

1. `del_task_list`: clear
2. `add_task_list`: once per task
3. `sav_task_list`: commit

```php
[
  'begDate' => '20191010',   // default: today
  'endDate' => '20291231',   // default: '20291231'
  'w1'..'w7' => '01',        // weekdays, default all '01'
  'sTime'  => '0630',        // start time HHmm, default '0000'
  'door'   => '01',          // 1-4, default '01'
  'task'   => '01',          // see below, default '00'
  'option' => '00',          // more cards required, default '00'
]
```

Task codes: `00` door controlled · `01` door open · `02` door closed · `03` disable
time profile · `04` enable time profile · `05` card, no password · `06` (in) card +
password · `07` (out) card + password · `08` multi-card off · `09` multi-card on ·
`10` trigger once (fw 3.9+) · `11` push-button off (fw 5.52+) · `12` push-button on
(fw 5.52+).

---

## 7. Fixed issues

These bugs were found while porting the library to Go (`Windows/uhppote/`) and are
fixed in the PHP code:

| # | Where | Was | Now |
|---|---|---|---|
| 1 | `open_door` | Only a PHP **int** door was used. Any string (everything from `sendCommand.php`) opened door 1. | Accepts `3` or `'03'`. Doors outside 1–4 return `false` instead of opening door 1. |
| 2 | `add_task_list` | The board serial was missing, so the start date landed where the serial belongs. | Serial is added like every other command. Default end date is now 2029-12-31 instead of the expired 2019-12-31. |
| 3 | `timer-example.php` | Missing comma, file did not parse. Socket helpers were missing. | Fixed. |
| 4 | `set-super-password-example.php` | Called `createSocket()` / `getReturnPacket()` without defining them. | Helpers added. |
| 5 | `sendCommand.php door_delay` | Sent only `seconds`, so the payload was shifted. | `door_delay <door> <seconds>`. |
| 6 | `sendCommand.php set_ripp` | Sent port 0. | `set_ripp <ip> [port=60002] [interval=0]`. |
| 7 | `sendCommand.php set_recordIndex` / `get_records` | Always used index 0 / record 1. | `set_recordIndex <index>`, `get_records [index]` (newest if omitted). |
| 8 | `sendCommand.php` help | `add_auth`, `set_ip`, `set_superPass` examples were wrong. `add_auth` doors were said to be optional but were required. | Examples fixed. `add_auth` doors are optional (default `01`). |
| 9 | `get_ip` | Listed but had no command code, built a broken packet. | Removed. `getCmdHex()` returns `false` for unknown commands. Use `search`. |
| 10 | `get_alarm_state` | Reply was `var_dump`ed. | Returns the raw payload. |
| 11 | All scripts | `socket_recv()` had no timeout, a wrong serial or IP hung forever. | 3-second receive timeout (`monitor.php` still waits forever for events). |
| 12 | `search.php` | Broadcast option used Linux-only numbers, failed on Windows. | Uses `SOL_SOCKET` / `SO_BROADCAST`. |
| 13 | Library | A missing serial called `die()`. Debug output could not be turned off. | Throws an `Exception`. `setDebug(false)` silences output. |

Still open:

- Comments state a max card end date of **2029-12-31**. Expect trouble with later dates.
- Most inputs are not validated. Malformed strings go straight into the packet.

---

## 8. Requirements & running

- PHP 5.4+ (short arrays, closures) with `ext-sockets` enabled. Written in the PHP 7
  era. Syntax and packet output are checked on PHP 7.4 and 8.3.
- The host must reach the board over UDP 60000. `search.php` needs broadcast, so it
  must run on the same subnet.
- For `monitor.php`, set `$localip` to an address of the machine running it and allow
  inbound UDP on `$localport` (60002 by default).

```console
$ php search.php
$ php -f sendCommand.php help
$ php -f sendCommand.php 192.168.1.50 22334455 dev_status
$ php -f sendCommand.php 192.168.1.50 22334455 add_auth 10012345 20240101 20291231 01 01 00 01
```

---

## 9. History & credits

- **CarbonSphere**: original author (2017). Core class, search, records, cards,
  time profiles, super passwords, task lists.
- **Jon Leach**: `monitor.php` (2017).
- **Curtis V. Schleich (Lincoln Christian University)**: `sendCommand.php` and
  implementations of set_ip, interlock, alarms, userid, keypad, super password,
  record index and selectable doors (Aug 2019).

Released under the MIT License, see [LICENSE](LICENSE).

---

## 10. Windows desktop app

[`Windows/`](Windows/README.md) holds **UHPPOTE Manager**, a native Windows desktop
app written in Go. It is a self-contained Go module:

| Path | What |
|---|---|
| `Windows/uhppote/` | Go port of the protocol, with tests checked against the PHP packets |
| `Windows/gui/` | The desktop app |
| `Windows/build.bat`, `Windows/build.sh` | Build `UHPPOTE-Manager.exe` on Windows, or cross-compile from Linux/WSL/macOS |
