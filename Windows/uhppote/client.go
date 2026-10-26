package uhppote

import (
	"errors"
	"fmt"
	"net"
	"sort"
	"sync"
	"time"
)

// ErrTimeout is returned when a controller does not answer in time.
var ErrTimeout = errors.New("no reply from controller (check IP address, serial number and firewall)")

// LogFunc receives every packet sent ("TX") or received ("RX").
type LogFunc func(direction string, addr *net.UDPAddr, p *Packet)

// Search broadcasts a search request and collects every controller that
// answers within timeout. If target is nil, the request is broadcast on every
// IPv4 network this PC is attached to; otherwise it is sent to target only
// (which may be a single controller IP or a directed broadcast address).
func Search(target net.IP, timeout time.Duration, log LogFunc) ([]*Device, error) {
	conn, err := net.ListenUDP("udp4", &net.UDPAddr{})
	if err != nil {
		return nil, err
	}
	defer conn.Close()

	var targets []net.IP
	if target != nil {
		targets = []net.IP{target}
	} else {
		targets = broadcastAddrs()
	}

	req := newRequest(CmdSearch, 0)
	sent := 0
	var lastErr error
	for _, ip := range targets {
		addr := &net.UDPAddr{IP: ip, Port: Port}
		if log != nil {
			log("TX", addr, req)
		}
		if _, err := conn.WriteToUDP(req[:], addr); err != nil {
			lastErr = err
			continue
		}
		sent++
	}
	if sent == 0 {
		return nil, fmt.Errorf("could not send search request: %w", lastErr)
	}

	found := map[uint32]*Device{}
	buf := make([]byte, 1024)
	deadline := time.Now().Add(timeout)
	_ = conn.SetReadDeadline(deadline)
	for time.Now().Before(deadline) {
		n, from, err := conn.ReadFromUDP(buf)
		if err != nil {
			var ne net.Error
			if errors.As(err, &ne) && ne.Timeout() {
				break
			}
			// Windows reports ICMP "port unreachable" from an earlier send as
			// a read error; ignore it and keep listening.
			continue
		}
		p, ok := toPacket(buf[:n])
		if !ok || p.Command() != CmdSearch {
			continue
		}
		if log != nil {
			log("RX", from, p)
		}
		d := parseDevice(p)
		found[d.Serial] = d
	}

	out := make([]*Device, 0, len(found))
	for _, d := range found {
		out = append(out, d)
	}
	sort.Slice(out, func(i, j int) bool { return out[i].Serial < out[j].Serial })
	return out, nil
}

// broadcastAddrs returns 255.255.255.255 plus the directed broadcast address
// of every active IPv4 interface, so that controllers on any attached network
// are reached even when the PC has several network adapters.
func broadcastAddrs() []net.IP {
	out := []net.IP{net.IPv4bcast}
	seen := map[string]bool{net.IPv4bcast.String(): true}
	ifaces, err := net.Interfaces()
	if err != nil {
		return out
	}
	for _, ifc := range ifaces {
		if ifc.Flags&net.FlagUp == 0 || ifc.Flags&net.FlagLoopback != 0 {
			continue
		}
		addrs, err := ifc.Addrs()
		if err != nil {
			continue
		}
		for _, a := range addrs {
			ipn, ok := a.(*net.IPNet)
			if !ok {
				continue
			}
			ip4 := ipn.IP.To4()
			if ip4 == nil || len(ipn.Mask) != net.IPv4len {
				continue
			}
			b := make(net.IP, 4)
			for i := range b {
				b[i] = ip4[i] | ^ipn.Mask[i]
			}
			if !seen[b.String()] {
				seen[b.String()] = true
				out = append(out, b)
			}
		}
	}
	return out
}

func toPacket(b []byte) (*Packet, bool) {
	if len(b) != PacketSize || b[0] != startOfFrame {
		return nil, false
	}
	p := &Packet{}
	copy(p[:], b)
	return p, true
}

// Controller talks to one board. It is safe for concurrent use; requests are
// sent one at a time.
type Controller struct {
	Serial  uint32
	Addr    *net.UDPAddr
	Timeout time.Duration
	Log     LogFunc

	mu sync.Mutex
}

// NewController returns a controller client for the board at ip.
func NewController(serial uint32, ip net.IP, log LogFunc) *Controller {
	return &Controller{
		Serial:  serial,
		Addr:    &net.UDPAddr{IP: ip, Port: Port},
		Timeout: 2500 * time.Millisecond,
		Log:     log,
	}
}

// exec sends req and waits for the matching reply.
func (c *Controller) exec(req *Packet) (*Packet, error) {
	c.mu.Lock()
	defer c.mu.Unlock()

	conn, err := net.DialUDP("udp4", nil, c.Addr)
	if err != nil {
		return nil, err
	}
	defer conn.Close()

	if c.Log != nil {
		c.Log("TX", c.Addr, req)
	}
	if _, err := conn.Write(req[:]); err != nil {
		return nil, err
	}

	buf := make([]byte, 1024)
	_ = conn.SetReadDeadline(time.Now().Add(c.Timeout))
	for {
		n, err := conn.Read(buf)
		if err != nil {
			var ne net.Error
			if errors.As(err, &ne) && ne.Timeout() {
				return nil, ErrTimeout
			}
			return nil, fmt.Errorf("no reply from %s: %w", c.Addr.IP, err)
		}
		p, ok := toPacket(buf[:n])
		if !ok || p.Command() != req.Command() || p.Serial() != c.Serial {
			continue
		}
		if c.Log != nil {
			c.Log("RX", c.Addr, p)
		}
		return p, nil
	}
}

// send transmits req without waiting for a reply.
func (c *Controller) send(req *Packet) error {
	c.mu.Lock()
	defer c.mu.Unlock()

	conn, err := net.DialUDP("udp4", nil, c.Addr)
	if err != nil {
		return err
	}
	defer conn.Close()
	if c.Log != nil {
		c.Log("TX", c.Addr, req)
	}
	_, err = conn.Write(req[:])
	return err
}

func (c *Controller) req(cmd byte) *Packet { return newRequest(cmd, c.Serial) }

func (c *Controller) execResult(req *Packet) (bool, error) {
	p, err := c.exec(req)
	if err != nil {
		return false, err
	}
	return p.result(), nil
}

// Status returns the controller's current state.
func (c *Controller) Status() (*Status, error) {
	p, err := c.exec(c.req(CmdStatus))
	if err != nil {
		return nil, err
	}
	return parseStatus(p), nil
}

// GetTime returns the controller clock.
func (c *Controller) GetTime() (time.Time, error) {
	p, err := c.exec(c.req(CmdGetTime))
	if err != nil {
		return time.Time{}, err
	}
	t, ok := p.dateTime(8)
	if !ok {
		return time.Time{}, fmt.Errorf("invalid time in reply: %s", p.bcd(8, 7))
	}
	return t, nil
}

// SetTime sets the controller clock and returns the time it now reports.
func (c *Controller) SetTime(t time.Time) (time.Time, error) {
	req := c.req(CmdSetTime)
	req.putDateTime(8, t)
	p, err := c.exec(req)
	if err != nil {
		return time.Time{}, err
	}
	got, _ := p.dateTime(8)
	return got, nil
}

// OpenDoor unlocks door 1-4 for its configured delay.
func (c *Controller) OpenDoor(door byte) (bool, error) {
	if err := checkDoor(door); err != nil {
		return false, err
	}
	req := c.req(CmdOpenDoor)
	req[8] = door
	return c.execResult(req)
}

// GetDoorControl returns a door's mode and unlock delay.
func (c *Controller) GetDoorControl(door byte) (DoorControl, error) {
	if err := checkDoor(door); err != nil {
		return DoorControl{}, err
	}
	req := c.req(CmdGetDoorControl)
	req[8] = door
	p, err := c.exec(req)
	if err != nil {
		return DoorControl{}, err
	}
	return DoorControl{Door: p[8], Mode: p[9], Delay: p[10]}, nil
}

// SetDoorControl sets a door's mode and unlock delay.
func (c *Controller) SetDoorControl(dc DoorControl) (DoorControl, error) {
	if err := checkDoor(dc.Door); err != nil {
		return DoorControl{}, err
	}
	req := c.req(CmdSetDoorControl)
	req[8], req[9], req[10] = dc.Door, dc.Mode, dc.Delay
	p, err := c.exec(req)
	if err != nil {
		return DoorControl{}, err
	}
	return DoorControl{Door: p[8], Mode: p[9], Delay: p[10]}, nil
}

// GetListener returns where the controller pushes live events.
func (c *Controller) GetListener() (Listener, error) {
	p, err := c.exec(c.req(CmdGetListener))
	if err != nil {
		return Listener{}, err
	}
	return Listener{IP: p.ip(8), Port: p.u16(12), Interval: p[14]}, nil
}

// SetListener sets where the controller pushes live events.
func (c *Controller) SetListener(l Listener) (bool, error) {
	req := c.req(CmdSetListener)
	if err := req.putIP(8, l.IP); err != nil {
		return false, err
	}
	req.putU16(12, l.Port)
	req[14] = l.Interval
	return c.execResult(req)
}

// GetCardCount returns the number of cards stored on the controller.
func (c *Controller) GetCardCount() (uint32, error) {
	p, err := c.exec(c.req(CmdGetCardCount))
	if err != nil {
		return 0, err
	}
	return p.u32(8), nil
}

// GetCard looks up a card. It returns nil, nil if the card is not stored.
func (c *Controller) GetCard(number uint32) (*Card, error) {
	if err := checkCard(number); err != nil {
		return nil, err
	}
	req := c.req(CmdGetCard)
	req.putU32(8, number)
	p, err := c.exec(req)
	if err != nil {
		return nil, err
	}
	got := p.u32(8)
	if got == 0 || got == 0xFFFFFFFF {
		return nil, nil
	}
	card := &Card{Number: got}
	card.From, _ = p.date(12)
	card.To, _ = p.date(16)
	copy(card.Doors[:], p[20:24])
	return card, nil
}

// PutCard adds or updates a card.
func (c *Controller) PutCard(card Card) (bool, error) {
	if err := checkCard(card.Number); err != nil {
		return false, err
	}
	req := c.req(CmdPutCard)
	req.putU32(8, card.Number)
	req.putDate(12, card.From)
	req.putDate(16, card.To)
	copy(req[20:24], card.Doors[:])
	return c.execResult(req)
}

// DeleteCard removes a card.
func (c *Controller) DeleteCard(number uint32) (bool, error) {
	if err := checkCard(number); err != nil {
		return false, err
	}
	req := c.req(CmdDeleteCard)
	req.putU32(8, number)
	return c.execResult(req)
}

// DeleteAllCards removes every card from the controller.
func (c *Controller) DeleteAllCards() (bool, error) {
	req := c.req(CmdDeleteAllCards)
	copy(req[8:], confirmMagic)
	return c.execResult(req)
}

// GetEventIndex returns the "already read" event index.
func (c *Controller) GetEventIndex() (uint32, error) {
	p, err := c.exec(c.req(CmdGetEventIndex))
	if err != nil {
		return 0, err
	}
	return p.u32(8), nil
}

// SetEventIndex sets the "already read" event index.
func (c *Controller) SetEventIndex(index uint32) (bool, error) {
	req := c.req(CmdSetEventIndex)
	req.putU32(8, index)
	copy(req[12:], confirmMagic)
	return c.execResult(req)
}

// NewestEvent can be passed to GetEvent to fetch the most recent event.
const NewestEvent uint32 = 0xFFFFFFFF

// GetEvent returns the event stored at index.
func (c *Controller) GetEvent(index uint32) (Event, error) {
	req := c.req(CmdGetEvent)
	req.putU32(8, index)
	p, err := c.exec(req)
	if err != nil {
		return Event{}, err
	}
	return parseEvent(p, 8), nil
}

// SetInterlock sets the door interlock mode (see InterlockModes).
func (c *Controller) SetInterlock(mode byte) (bool, error) {
	req := c.req(CmdSetInterlock)
	req[8] = mode
	return c.execResult(req)
}

// SetKeypads enables or disables the keypad on each reader.
func (c *Controller) SetKeypads(enabled [4]bool) (bool, error) {
	req := c.req(CmdSetKeypads)
	for i, on := range enabled {
		if on {
			req[8+i] = 0x01
		}
	}
	return c.execResult(req)
}

// SetAddress changes the controller's IP settings. The controller does not
// reply; search again afterwards to confirm the change.
func (c *Controller) SetAddress(ip, mask, gateway net.IP) error {
	req := c.req(CmdSetAddress)
	if err := req.putIP(8, ip); err != nil {
		return err
	}
	if err := req.putIP(12, mask); err != nil {
		return err
	}
	if err := req.putIP(16, gateway); err != nil {
		return err
	}
	copy(req[20:], confirmMagic)
	return c.send(req)
}

func checkDoor(door byte) error {
	if door < 1 || door > 4 {
		return fmt.Errorf("door must be 1-4, got %d", door)
	}
	return nil
}

func checkCard(number uint32) error {
	if number == 0 || number >= 0xFFFFFF00 {
		return fmt.Errorf("invalid card number %d", number)
	}
	return nil
}
