// Package uhppote implements the UDP protocol spoken by UHPPOTE / WeiGeng
// Wiegand access controller boards (firmware V6.56+).
//
// Every request and reply is a fixed 64-byte packet:
//
//	offset 0  : 0x17 (start of frame)
//	offset 1  : command code
//	offset 2-3: 0x0000
//	offset 4-7: controller serial number, uint32 little-endian
//	offset 8+ : command payload, zero padded
//
// Multi-byte integers are little-endian, IP addresses are in network order and
// dates/times are BCD ("20190522" is sent as the bytes 20 19 05 22).
package uhppote

import (
	"encoding/binary"
	"encoding/hex"
	"fmt"
	"net"
	"strings"
	"time"
)

const (
	// Port is the UDP port every controller listens on.
	Port = 60000

	// PacketSize is the fixed size of requests and replies.
	PacketSize = 64

	startOfFrame = 0x17
)

// Command codes.
const (
	CmdStatus         byte = 0x20
	CmdSetTime        byte = 0x30
	CmdGetTime        byte = 0x32
	CmdOpenDoor       byte = 0x40
	CmdPutCard        byte = 0x50
	CmdDeleteCard     byte = 0x52
	CmdDeleteAllCards byte = 0x54
	CmdGetCardCount   byte = 0x58
	CmdGetCard        byte = 0x5A
	CmdSetDoorControl byte = 0x80
	CmdGetDoorControl byte = 0x82
	CmdSetListener    byte = 0x90
	CmdGetListener    byte = 0x92
	CmdSearch         byte = 0x94
	CmdSetAddress     byte = 0x96
	CmdSetInterlock   byte = 0xA2
	CmdSetKeypads     byte = 0xA4
	CmdGetEvent       byte = 0xB0
	CmdSetEventIndex  byte = 0xB2
	CmdGetEventIndex  byte = 0xB4
)

// confirmMagic must follow the payload of destructive commands.
var confirmMagic = []byte{0x55, 0xAA, 0xAA, 0x55}

// Packet is a raw 64-byte request or reply.
type Packet [PacketSize]byte

func newRequest(cmd byte, serial uint32) *Packet {
	p := &Packet{}
	p[0] = startOfFrame
	p[1] = cmd
	binary.LittleEndian.PutUint32(p[4:8], serial)
	return p
}

// Command returns the command code of the packet.
func (p *Packet) Command() byte { return p[1] }

// Serial returns the controller serial number carried in the packet.
func (p *Packet) Serial() uint32 { return binary.LittleEndian.Uint32(p[4:8]) }

// Hex returns the packet as space-separated hex bytes, for logging.
func (p *Packet) Hex() string { return HexBytes(p[:]) }

// HexBytes formats b as space-separated upper-case hex bytes.
func HexBytes(b []byte) string {
	s := strings.ToUpper(hex.EncodeToString(b))
	var sb strings.Builder
	for i := 0; i < len(s); i += 2 {
		if i > 0 {
			sb.WriteByte(' ')
		}
		sb.WriteString(s[i : i+2])
	}
	return sb.String()
}

func (p *Packet) u16(off int) uint16 { return binary.LittleEndian.Uint16(p[off:]) }
func (p *Packet) u32(off int) uint32 { return binary.LittleEndian.Uint32(p[off:]) }

func (p *Packet) putU16(off int, v uint16) { binary.LittleEndian.PutUint16(p[off:], v) }
func (p *Packet) putU32(off int, v uint32) { binary.LittleEndian.PutUint32(p[off:], v) }

func (p *Packet) ip(off int) net.IP {
	return net.IPv4(p[off], p[off+1], p[off+2], p[off+3]).To4()
}

func (p *Packet) putIP(off int, ip net.IP) error {
	v4 := ip.To4()
	if v4 == nil {
		return fmt.Errorf("%q is not an IPv4 address", ip)
	}
	copy(p[off:off+4], v4)
	return nil
}

// bcd returns n bytes at off as a string of 2n decimal digits.
func (p *Packet) bcd(off, n int) string {
	return hex.EncodeToString(p[off : off+n])
}

// putBCD writes a string of decimal digits (even length) as BCD bytes.
func (p *Packet) putBCD(off int, digits string) error {
	if len(digits)%2 != 0 {
		return fmt.Errorf("BCD value %q has odd length", digits)
	}
	for _, c := range digits {
		if c < '0' || c > '9' {
			return fmt.Errorf("BCD value %q is not numeric", digits)
		}
	}
	b, _ := hex.DecodeString(digits)
	copy(p[off:], b)
	return nil
}

func (p *Packet) putDate(off int, t time.Time) {
	_ = p.putBCD(off, t.Format("20060102"))
}

// date decodes a 4-byte BCD YYYYMMDD date. ok is false for an empty or invalid date.
func (p *Packet) date(off int) (t time.Time, ok bool) {
	t, err := time.ParseInLocation("20060102", p.bcd(off, 4), time.Local)
	return t, err == nil
}

func (p *Packet) putDateTime(off int, t time.Time) {
	_ = p.putBCD(off, t.Format("20060102150405"))
}

// dateTime decodes a 7-byte BCD YYYYMMDDhhmmss timestamp.
func (p *Packet) dateTime(off int) (t time.Time, ok bool) {
	t, err := time.ParseInLocation("20060102150405", p.bcd(off, 7), time.Local)
	return t, err == nil
}

func (p *Packet) result() bool { return p[8] == 0x01 }
