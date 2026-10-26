package uhppote

import (
	"encoding/hex"
	"net"
	"strings"
	"testing"
	"time"
)

const testSerial uint32 = 0x11223344 // setSn("11223344") in the PHP library

// fakeController answers requests on a loopback port and records the last
// request it received.
func fakeController(t *testing.T, reply func(req *Packet) *Packet) (*Controller, *Packet) {
	t.Helper()
	conn, err := net.ListenUDP("udp4", &net.UDPAddr{IP: net.IPv4(127, 0, 0, 1)})
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { conn.Close() })
	last := &Packet{}
	go func() {
		buf := make([]byte, 1024)
		for {
			n, from, err := conn.ReadFromUDP(buf)
			if err != nil {
				return
			}
			req, ok := toPacket(buf[:n])
			if !ok {
				continue
			}
			*last = *req
			if r := reply(req); r != nil {
				conn.WriteToUDP(r[:], from)
			}
		}
	}()
	c := NewController(testSerial, net.IPv4(127, 0, 0, 1), nil)
	c.Addr = conn.LocalAddr().(*net.UDPAddr)
	c.Timeout = time.Second
	return c, last
}

// echoResult replies with the request header and a success byte.
func echoResult(req *Packet) *Packet {
	r := newRequest(req.Command(), req.Serial())
	r[8] = 0x01
	return r
}

// wantHex compares the start of p with the expected hex prefix and checks the
// rest is zero padding.
func wantHex(t *testing.T, p *Packet, prefix string) {
	t.Helper()
	prefix = strings.ReplaceAll(prefix, " ", "")
	want := prefix + strings.Repeat("0", PacketSize*2-len(prefix))
	if got := hex.EncodeToString(p[:]); !strings.EqualFold(got, want) {
		t.Errorf("packet mismatch\n got  %s\n want %s", got, strings.ToLower(want))
	}
}

func TestOpenDoorMatchesPHP(t *testing.T) {
	c, last := fakeController(t, echoResult)
	ok, err := c.OpenDoor(3)
	if err != nil || !ok {
		t.Fatalf("OpenDoor: %v %v", ok, err)
	}
	wantHex(t, last, "17400000 44332211 03")
}

func TestPutCardMatchesPHP(t *testing.T) {
	c, last := fakeController(t, echoResult)
	from := time.Date(2019, 5, 22, 0, 0, 0, 0, time.Local)
	to := time.Date(2020, 5, 22, 0, 0, 0, 0, time.Local)
	ok, err := c.PutCard(Card{Number: 10012345, From: from, To: to, Doors: [4]byte{1, 1, 0, 2}})
	if err != nil || !ok {
		t.Fatalf("PutCard: %v %v", ok, err)
	}
	wantHex(t, last, "17500000 44332211 B9C69800 20190522 20200522 01010002")
}

func TestSetListenerMatchesPHP(t *testing.T) {
	c, last := fakeController(t, echoResult)
	if _, err := c.SetListener(Listener{IP: net.ParseIP("192.168.1.2"), Port: 60002}); err != nil {
		t.Fatal(err)
	}
	wantHex(t, last, "17900000 44332211 C0A80102 62EA 00")
}

func TestSetTimeMatchesPHP(t *testing.T) {
	c, last := fakeController(t, func(req *Packet) *Packet {
		r := newRequest(req.Command(), req.Serial())
		copy(r[8:15], req[8:15])
		return r
	})
	want := time.Date(2019, 11, 1, 12, 34, 56, 0, time.Local)
	got, err := c.SetTime(want)
	if err != nil {
		t.Fatal(err)
	}
	if !got.Equal(want) {
		t.Errorf("SetTime returned %v, want %v", got, want)
	}
	wantHex(t, last, "17300000 44332211 20191101123456")
}

func TestDeleteAllAndEventIndexSendMagic(t *testing.T) {
	c, last := fakeController(t, echoResult)
	if _, err := c.DeleteAllCards(); err != nil {
		t.Fatal(err)
	}
	wantHex(t, last, "17540000 44332211 55AAAA55")
	if _, err := c.SetEventIndex(7); err != nil {
		t.Fatal(err)
	}
	wantHex(t, last, "17B20000 44332211 07000000 55AAAA55")
}

func TestDoorControl(t *testing.T) {
	c, last := fakeController(t, func(req *Packet) *Packet {
		r := newRequest(req.Command(), req.Serial())
		r[8], r[9], r[10] = req[8], ModeControlled, 5
		return r
	})
	dc, err := c.GetDoorControl(2)
	if err != nil {
		t.Fatal(err)
	}
	wantHex(t, last, "17820000 44332211 02")
	if dc != (DoorControl{Door: 2, Mode: ModeControlled, Delay: 5}) {
		t.Errorf("got %+v", dc)
	}
	if _, err := c.SetDoorControl(DoorControl{Door: 1, Mode: ModeControlled, Delay: 5}); err != nil {
		t.Fatal(err)
	}
	wantHex(t, last, "17800000 44332211 01 03 05")
}

func TestGetCard(t *testing.T) {
	c, _ := fakeController(t, func(req *Packet) *Packet {
		r := newRequest(req.Command(), req.Serial())
		if req.u32(8) == 10012345 {
			copy(r[8:], req[8:12])
			r.putBCD(12, "2024010120291231")
			copy(r[20:24], []byte{1, 0, 5, 1})
		}
		return r
	})
	card, err := c.GetCard(10012345)
	if err != nil || card == nil {
		t.Fatalf("GetCard: %v %v", card, err)
	}
	if card.From.Format("2006-01-02") != "2024-01-01" || card.To.Format("2006-01-02") != "2029-12-31" {
		t.Errorf("dates: %v %v", card.From, card.To)
	}
	if card.Doors != [4]byte{1, 0, 5, 1} {
		t.Errorf("doors: %v", card.Doors)
	}
	missing, err := c.GetCard(42)
	if err != nil || missing != nil {
		t.Errorf("missing card: %v %v", missing, err)
	}
}

func TestReplyFromOtherSerialIgnored(t *testing.T) {
	c, _ := fakeController(t, func(req *Packet) *Packet {
		return newRequest(req.Command(), req.Serial()+1)
	})
	c.Timeout = 200 * time.Millisecond
	if _, err := c.OpenDoor(1); err != ErrTimeout {
		t.Errorf("want ErrTimeout, got %v", err)
	}
}

func TestInvalidInputsRejected(t *testing.T) {
	c := NewController(testSerial, net.IPv4(127, 0, 0, 1), nil)
	if _, err := c.OpenDoor(5); err == nil {
		t.Error("door 5 accepted")
	}
	if _, err := c.DeleteCard(0); err == nil {
		t.Error("card 0 accepted")
	}
}

func mustPacket(t *testing.T, h string) *Packet {
	t.Helper()
	h = strings.ReplaceAll(h, " ", "")
	b, err := hex.DecodeString(h + strings.Repeat("0", PacketSize*2-len(h)))
	if err != nil {
		t.Fatal(err)
	}
	p, ok := toPacket(b)
	if !ok {
		t.Fatal("bad packet")
	}
	return p
}

func TestParseDevice(t *testing.T) {
	p := mustPacket(t, "17940000 78563412 C0A80132 FFFFFF00 C0A80101 001122334455 0662 20180816")
	d := parseDevice(p)
	if d.Serial != 305419896 {
		t.Errorf("serial %d", d.Serial)
	}
	if d.IP.String() != "192.168.1.50" || d.Mask.String() != "255.255.255.0" || d.Gateway.String() != "192.168.1.1" {
		t.Errorf("addresses %v %v %v", d.IP, d.Mask, d.Gateway)
	}
	if d.MAC.String() != "00:11:22:33:44:55" {
		t.Errorf("mac %v", d.MAC)
	}
	if d.Firmware != "6.62" || d.Date != "2018-08-16" {
		t.Errorf("firmware %q date %q", d.Firmware, d.Date)
	}
}

func TestParseStatus(t *testing.T) {
	p := mustPacket(t, "17200000 44332211"+
		"05000000 01 01 02 01 B9C69800 20240315093000 01"+ // last event
		"01000000"+ // doors 1 open
		"00010000"+ // button 2 pressed
		"00 101530 2A000000 00000000 00 01 00 240315")
	s := parseStatus(p)
	e := s.LastEvent
	if e.Index != 5 || e.Type != 1 || !e.Granted || e.Door != 2 || e.Direction != 1 || e.Card != 10012345 {
		t.Errorf("event %+v", e)
	}
	if !e.HasTime || e.Timestamp.Format("2006-01-02 15:04:05") != "2024-03-15 09:30:00" {
		t.Errorf("event time %v", e.Timestamp)
	}
	if s.DoorOpen != [4]bool{true, false, false, false} || s.ButtonPressed != [4]bool{false, true, false, false} {
		t.Errorf("doors %v buttons %v", s.DoorOpen, s.ButtonPressed)
	}
	if !s.HasTime || s.ControllerTime.Format("2006-01-02 15:04:05") != "2024-03-15 10:15:30" {
		t.Errorf("controller time %v", s.ControllerTime)
	}
	if s.SequenceNo != 42 || s.Relays != 1 {
		t.Errorf("seq %d relays %d", s.SequenceNo, s.Relays)
	}
}

func TestBroadcastAddrsIncludesLimitedBroadcast(t *testing.T) {
	addrs := broadcastAddrs()
	if len(addrs) == 0 || !addrs[0].Equal(net.IPv4bcast) {
		t.Errorf("got %v", addrs)
	}
}
