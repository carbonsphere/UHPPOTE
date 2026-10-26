package uhppote

import (
	"fmt"
	"net"
	"time"
)

// Device is a controller found by Search.
type Device struct {
	Serial   uint32
	IP       net.IP
	Mask     net.IP
	Gateway  net.IP
	MAC      net.HardwareAddr
	Firmware string // e.g. "6.62"
	Date     string // firmware release date, YYYY-MM-DD
}

func parseDevice(p *Packet) *Device {
	ver := p.bcd(26, 2)
	d := p.bcd(28, 4)
	return &Device{
		Serial:   p.Serial(),
		IP:       p.ip(8),
		Mask:     p.ip(12),
		Gateway:  p.ip(16),
		MAC:      net.HardwareAddr(append([]byte(nil), p[20:26]...)),
		Firmware: fmt.Sprintf("%d.%s", atoiOr(ver[:2], 0), ver[2:]),
		Date:     d[0:4] + "-" + d[4:6] + "-" + d[6:8],
	}
}

// Event is a swipe, door or alarm record stored by the controller.
type Event struct {
	Index     uint32
	Type      byte
	Granted   bool
	Door      byte
	Direction byte
	Card      uint32
	Timestamp time.Time
	HasTime   bool
	Reason    byte
}

func parseEvent(p *Packet, off int) Event {
	e := Event{
		Index:     p.u32(off),
		Type:      p[off+4],
		Granted:   p[off+5] == 0x01,
		Door:      p[off+6],
		Direction: p[off+7],
		Card:      p.u32(off + 8),
		Reason:    p[off+19],
	}
	e.Timestamp, e.HasTime = p.dateTime(off + 12)
	return e
}

// Empty reports whether the slot holds no event.
func (e Event) Empty() bool { return e.Type == 0x00 && e.Index == 0 }

// TypeText describes the event type.
func (e Event) TypeText() string {
	switch e.Type {
	case 0x00:
		return "None"
	case 0x01:
		return "Card swipe"
	case 0x02:
		return "Door / button"
	case 0x03:
		return "Alarm"
	case 0xFF:
		return "Overwritten"
	}
	return fmt.Sprintf("Type %d", e.Type)
}

// DirectionText describes the reader direction.
func (e Event) DirectionText() string {
	switch e.Direction {
	case 0x01:
		return "In"
	case 0x02:
		return "Out"
	}
	return "-"
}

// eventReasons maps reason codes to text. The list is best-effort; unknown
// codes are shown by number.
var eventReasons = map[byte]string{
	1:  "Swipe",
	5:  "Denied: PC control",
	6:  "Denied: no access rights",
	7:  "Denied: wrong password",
	8:  "Denied: anti-passback",
	9:  "Denied: more cards required",
	10: "Denied: first card open",
	11: "Denied: door normally closed",
	12: "Denied: interlock",
	13: "Denied: outside time profile",
	15: "Denied: invalid time zone",
	18: "Denied",
	20: "Push button",
	23: "Door opened",
	24: "Door closed",
	25: "Door opened by super password",
	28: "Controller power on",
	29: "Controller reset",
	31: "Push button denied: door locked",
	32: "Push button denied: offline",
	33: "Push button denied: interlock",
	34: "Push button denied: threat",
	37: "Door open too long",
	38: "Forced open",
	39: "Fire",
	40: "Forced closed",
	41: "Anti-theft",
	42: "24x7 zone",
	43: "Emergency",
	44: "Remote open door",
	45: "Remote open door (USB reader)",
}

// ReasonText describes the event reason code.
func (e Event) ReasonText() string {
	if s, ok := eventReasons[e.Reason]; ok {
		return fmt.Sprintf("%s (%d)", s, e.Reason)
	}
	return fmt.Sprintf("Reason %d", e.Reason)
}

// Status is the controller's current state, as returned by CmdStatus.
type Status struct {
	LastEvent      Event
	DoorOpen       [4]bool
	ButtonPressed  [4]bool
	SystemError    byte
	ControllerTime time.Time
	HasTime        bool
	SequenceNo     uint32
	SpecialInfo    byte
	Relays         byte // bitmask, bit 0 = door 1
	Inputs         byte // bitmask
}

func parseStatus(p *Packet) *Status {
	s := &Status{
		LastEvent:   parseEvent(p, 8),
		SystemError: p[36],
		SequenceNo:  p.u32(40),
		SpecialInfo: p[48],
		Relays:      p[49],
		Inputs:      p[50],
	}
	for i := 0; i < 4; i++ {
		s.DoorOpen[i] = p[28+i] == 0x01
		s.ButtonPressed[i] = p[32+i] == 0x01
	}
	// Date is YYMMDD at 51, time is hhmmss at 37.
	s.ControllerTime, s.HasTime = parseTimeIn("060102150405", p.bcd(51, 3)+p.bcd(37, 3))
	return s
}

func parseTimeIn(layout, value string) (time.Time, bool) {
	t, err := time.ParseInLocation(layout, value, time.Local)
	return t, err == nil
}

// Card is an access card stored on the controller.
type Card struct {
	Number uint32
	From   time.Time
	To     time.Time
	// Doors holds the rule for doors 1-4: 0 = no access, 1 = always allowed,
	// 2-254 = time profile index.
	Doors [4]byte
}

// Door control modes.
const (
	ModeNormallyOpen   byte = 1
	ModeNormallyClosed byte = 2
	ModeControlled     byte = 3
)

// DoorControl is a door's lock mode and unlock delay.
type DoorControl struct {
	Door  byte // 1-4
	Mode  byte // ModeNormallyOpen, ModeNormallyClosed or ModeControlled
	Delay byte // seconds
}

// ModeText describes the door control mode.
func (d DoorControl) ModeText() string {
	switch d.Mode {
	case ModeNormallyOpen:
		return "Normally open"
	case ModeNormallyClosed:
		return "Normally closed"
	case ModeControlled:
		return "Controlled"
	}
	return fmt.Sprintf("Mode %d", d.Mode)
}

// Listener is where the controller pushes live events.
type Listener struct {
	IP       net.IP
	Port     uint16
	Interval byte // seconds between periodic status packets, 0 = events only
}

// Interlock modes.
var InterlockModes = []struct {
	Value byte
	Text  string
}{
	{0x00, "No interlock"},
	{0x01, "Doors 1 & 2"},
	{0x02, "Doors 3 & 4"},
	{0x03, "Doors 1 & 2, and doors 3 & 4"},
	{0x04, "Doors 1, 2 & 3"},
	{0x08, "Doors 1, 2, 3 & 4"},
}

func atoiOr(s string, def int) int {
	n := 0
	if s == "" {
		return def
	}
	for _, c := range s {
		if c < '0' || c > '9' {
			return def
		}
		n = n*10 + int(c-'0')
	}
	return n
}
