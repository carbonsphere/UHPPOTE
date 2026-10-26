//go:build windows

package main

import (
	"fmt"
	"strconv"
	"strings"
	"time"

	"github.com/tailscale/walk"
	. "github.com/tailscale/walk/declarative"

	"github.com/carbonsphere/UHPPOTE/Windows/uhppote"
)

const (
	autoRefreshEvery = 2 * time.Second
	maxEventFetch    = 500
)

var (
	doorNames = []string{"Door 1", "Door 2", "Door 3", "Door 4"}
	modeNames = []string{"Normally open", "Normally closed", "Controlled"} // index+1 = protocol mode
)

// accessNames lists door access rules: index 0 = no access, 1 = allowed,
// 2-254 = time profile number.
var accessNames = func() []string {
	names := []string{"No access", "Allowed"}
	for i := 2; i <= 254; i++ {
		names = append(names, fmt.Sprintf("Time profile %d", i))
	}
	return names
}()

func note(text string) Label {
	return Label{Text: text, TextColor: walk.RGB(0x60, 0x60, 0x60)}
}

// ── Status ────────────────────────────────────────────────────────────────

func (a *App) statusPage() TabPage {
	return TabPage{
		Title:  "Status",
		Layout: VBox{},
		Children: []Widget{
			Composite{
				Layout: HBox{MarginsZero: true},
				Children: []Widget{
					PushButton{Text: "Refresh", OnClicked: a.refreshStatus},
					CheckBox{
						AssignTo:            &a.autoRefresh,
						Text:                fmt.Sprintf("Auto-refresh every %d s", int(autoRefreshEvery.Seconds())),
						OnCheckStateChanged: func() { a.setAutoRefresh(a.autoRefresh.Checked()) },
					},
					HSpacer{},
				},
			},
			TableView{
				Model:                    a.doorsModel,
				NotSortableByHeaderClick: true,
				LastColumnStretched:      true,
				MinSize:                  Size{Height: 110},
				MaxSize:                  Size{Height: 110},
				Columns: []TableViewColumn{
					{Title: "Door", Width: 70},
					{Title: "Door sensor", Width: 110},
					{Title: "Push button", Width: 110},
					{Title: "Lock relay"},
				},
			},
			TableView{
				Model:                    a.infoModel,
				NotSortableByHeaderClick: true,
				LastColumnStretched:      true,
				Columns: []TableViewColumn{
					{Title: "Item", Width: 170},
					{Title: "Value"},
				},
			},
		},
	}
}

func (a *App) refreshStatus() {
	a.run("Read status", func(c *uhppote.Controller) (func(), error) {
		st, err := c.Status()
		if err != nil {
			return nil, err
		}
		return func() { a.showStatus(st) }, nil
	})
}

func (a *App) showStatus(st *uhppote.Status) {
	doors := make([][]string, 4)
	for i := 0; i < 4; i++ {
		doors[i] = []string{
			doorNames[i],
			yesNo(st.DoorOpen[i], "OPEN", "Closed"),
			yesNo(st.ButtonPressed[i], "Pressed", "Released"),
			yesNo(st.Relays&(1<<i) != 0, "Unlocked", "Locked"),
		}
	}
	a.doorsModel.set(doors)

	e := st.LastEvent
	rows := [][]string{
		{"Controller time", fmtTime(st.ControllerTime, st.HasTime)},
		{"System error", yesNo(st.SystemError == 0, "None", fmt.Sprintf("Error code %d", st.SystemError))},
		{"Packet sequence no.", fmt.Sprint(st.SequenceNo)},
		{"Special info", fmt.Sprintf("0x%02X", st.SpecialInfo)},
		{"Relay state", fmt.Sprintf("0x%02X", st.Relays)},
		{"Input state", fmt.Sprintf("0x%02X", st.Inputs)},
		{"", ""},
	}
	if e.Empty() {
		rows = append(rows, []string{"Last event", "No events recorded"})
	} else {
		rows = append(rows,
			[]string{"Last event #", fmt.Sprint(e.Index)},
			[]string{"  Time", fmtTime(e.Timestamp, e.HasTime)},
			[]string{"  Type", e.TypeText()},
			[]string{"  Card", fmt.Sprint(e.Card)},
			[]string{"  Door / direction", fmt.Sprintf("%d / %s", e.Door, e.DirectionText())},
			[]string{"  Access", yesNo(e.Granted, "Granted", "Denied")},
			[]string{"  Reason", e.ReasonText()},
		)
	}
	a.infoModel.set(rows)
}

// setAutoRefresh starts or stops periodic status polling.
func (a *App) setAutoRefresh(on bool) {
	if a.stopAuto != nil {
		close(a.stopAuto)
		a.stopAuto = nil
	}
	if !on || a.ctrl == nil {
		return
	}
	stop := make(chan struct{})
	a.stopAuto = stop
	c := a.ctrl
	go func() {
		t := time.NewTicker(autoRefreshEvery)
		defer t.Stop()
		for {
			select {
			case <-stop:
				return
			case <-t.C:
				if !a.refreshing.CompareAndSwap(false, true) {
					continue
				}
				st, err := c.Status()
				a.refreshing.Store(false)
				a.ui(func() {
					if a.ctrl != c {
						return
					}
					if err != nil {
						a.setStatus("Auto-refresh: %v", err)
						return
					}
					a.showStatus(st)
					a.setStatus("Status updated %s", time.Now().Format("15:04:05"))
				})
			}
		}
	}()
}

// ── Doors ─────────────────────────────────────────────────────────────────

func (a *App) doorsPage() TabPage {
	interlocks := make([]string, len(uhppote.InterlockModes))
	for i, m := range uhppote.InterlockModes {
		interlocks[i] = m.Text
	}
	return TabPage{
		Title:  "Doors",
		Layout: VBox{},
		Children: []Widget{
			GroupBox{
				Title:  "Door",
				Layout: Grid{Columns: 4},
				Children: []Widget{
					Label{Text: "Door:"},
					ComboBox{
						AssignTo:              &a.doorSel,
						Model:                 doorNames,
						CurrentIndex:          0,
						OnCurrentIndexChanged: a.readDoorControl,
					},
					PushButton{Text: "Open door", MinSize: Size{Width: 120}, OnClicked: a.openDoor},
					HSpacer{},

					Label{Text: "Lock mode:"},
					ComboBox{AssignTo: &a.modeSel, Model: modeNames, CurrentIndex: 2},
					Label{Text: "Unlock time:"},
					NumberEdit{AssignTo: &a.delayEdit, MinValue: 0, MaxValue: 255, Suffix: " s", SpinButtonsVisible: true, MaxSize: Size{Width: 90}},

					PushButton{Text: "Read", OnClicked: a.readDoorControl},
					PushButton{Text: "Save", OnClicked: a.writeDoorControl},
					HSpacer{ColumnSpan: 2},
				},
			},
			GroupBox{
				Title:  "Interlock",
				Layout: HBox{},
				Children: []Widget{
					ComboBox{AssignTo: &a.interlockSel, Model: interlocks, CurrentIndex: 0},
					PushButton{Text: "Apply", OnClicked: a.writeInterlock},
					HSpacer{},
				},
			},
			GroupBox{
				Title:  "Reader keypads",
				Layout: HBox{},
				Children: []Widget{
					CheckBox{AssignTo: &a.keypads[0], Text: "Reader 1"},
					CheckBox{AssignTo: &a.keypads[1], Text: "Reader 2"},
					CheckBox{AssignTo: &a.keypads[2], Text: "Reader 3"},
					CheckBox{AssignTo: &a.keypads[3], Text: "Reader 4"},
					PushButton{Text: "Apply", OnClicked: a.writeKeypads},
					HSpacer{},
				},
			},
			note("Interlock and keypad settings can only be written; the controller has no command to read them back."),
			VSpacer{},
		},
	}
}

func (a *App) selectedDoor() byte { return byte(a.doorSel.CurrentIndex() + 1) }

func (a *App) openDoor() {
	door := a.selectedDoor()
	a.run(fmt.Sprintf("Open door %d", door), func(c *uhppote.Controller) (func(), error) {
		return nil, result(c.OpenDoor(door))
	})
}

func (a *App) showDoorControl(dc uhppote.DoorControl) {
	if dc.Mode >= 1 && int(dc.Mode) <= len(modeNames) {
		a.modeSel.SetCurrentIndex(int(dc.Mode) - 1)
	}
	a.delayEdit.SetValue(float64(dc.Delay))
}

func (a *App) readDoorControl() {
	if a.ctrl == nil {
		return
	}
	door := a.selectedDoor()
	a.run(fmt.Sprintf("Read door %d settings", door), func(c *uhppote.Controller) (func(), error) {
		dc, err := c.GetDoorControl(door)
		if err != nil {
			return nil, err
		}
		return func() { a.showDoorControl(dc) }, nil
	})
}

func (a *App) writeDoorControl() {
	dc := uhppote.DoorControl{
		Door:  a.selectedDoor(),
		Mode:  byte(a.modeSel.CurrentIndex() + 1),
		Delay: byte(a.delayEdit.Value()),
	}
	a.run(fmt.Sprintf("Save door %d settings (%s, %d s)", dc.Door, dc.ModeText(), dc.Delay), func(c *uhppote.Controller) (func(), error) {
		got, err := c.SetDoorControl(dc)
		if err != nil {
			return nil, err
		}
		return func() { a.showDoorControl(got) }, nil
	})
}

func (a *App) writeInterlock() {
	m := uhppote.InterlockModes[a.interlockSel.CurrentIndex()]
	a.run("Set interlock: "+m.Text, func(c *uhppote.Controller) (func(), error) {
		return nil, result(c.SetInterlock(m.Value))
	})
}

func (a *App) writeKeypads() {
	var on [4]bool
	for i, cb := range a.keypads {
		on[i] = cb.Checked()
	}
	a.run("Set reader keypads", func(c *uhppote.Controller) (func(), error) {
		return nil, result(c.SetKeypads(on))
	})
}

// ── Cards ─────────────────────────────────────────────────────────────────

func (a *App) cardsPage() TabPage {
	doorRows := []Widget{}
	for i := 0; i < 4; i++ {
		doorRows = append(doorRows,
			Label{Text: doorNames[i] + ":"},
			ComboBox{AssignTo: &a.cardDoors[i], Model: accessNames, CurrentIndex: 1},
		)
	}
	return TabPage{
		Title:  "Cards",
		Layout: VBox{},
		Children: []Widget{
			Composite{
				Layout: HBox{MarginsZero: true},
				Children: []Widget{
					Label{Text: "Cards stored:"},
					Label{AssignTo: &a.cardCount, Text: "-", Font: Font{Bold: true}},
					PushButton{Text: "Refresh", OnClicked: a.readCardCount},
					HSpacer{},
				},
			},
			GroupBox{
				Title:  "Card",
				Layout: Grid{Columns: 4},
				Children: append([]Widget{
					Label{Text: "Card number:"},
					LineEdit{AssignTo: &a.cardNumber, CueBanner: "e.g. 10012345"},
					PushButton{Text: "Look up", OnClicked: a.lookupCard},
					HSpacer{},

					Label{Text: "Valid from:"},
					DateEdit{AssignTo: &a.cardFrom, Format: "yyyy-MM-dd"},
					Label{Text: "Valid to:"},
					DateEdit{AssignTo: &a.cardTo, Format: "yyyy-MM-dd"},
				}, append(doorRows,
					PushButton{Text: "Add / update card", OnClicked: a.putCard},
					PushButton{Text: "Delete card", OnClicked: a.deleteCard},
					HSpacer{ColumnSpan: 2},
				)...),
			},
			note("The card number is the decimal number the reader sends (facility code and card code combined, e.g. 100 + 12345 → 10012345)."),
			note("Time profiles (2–254) must already exist on the controller."),
			VSpacer{},
			GroupBox{
				Title:  "Danger zone",
				Layout: HBox{},
				Children: []Widget{
					PushButton{Text: "Delete ALL cards…", OnClicked: a.deleteAllCards},
					HSpacer{},
				},
			},
		},
	}
}

func (a *App) cardNumberValue() (uint32, bool) {
	s := strings.TrimSpace(a.cardNumber.Text())
	n, err := strconv.ParseUint(s, 10, 32)
	if err != nil || n == 0 {
		a.warn("Card", fmt.Sprintf("%q is not a valid card number.", s))
		return 0, false
	}
	return uint32(n), true
}

func (a *App) readCardCount() {
	a.run("Read card count", func(c *uhppote.Controller) (func(), error) {
		n, err := c.GetCardCount()
		if err != nil {
			return nil, err
		}
		return func() { a.cardCount.SetText(fmt.Sprint(n)) }, nil
	})
}

func (a *App) lookupCard() {
	n, ok := a.cardNumberValue()
	if !ok {
		return
	}
	a.run(fmt.Sprintf("Look up card %d", n), func(c *uhppote.Controller) (func(), error) {
		card, err := c.GetCard(n)
		if err != nil {
			return nil, err
		}
		return func() {
			if card == nil {
				a.logf("Card %d is not stored on the controller.", n)
				a.setStatus("Card %d not found.", n)
				return
			}
			a.cardFrom.SetDate(card.From)
			a.cardTo.SetDate(card.To)
			for i, cb := range a.cardDoors {
				if int(card.Doors[i]) < len(accessNames) {
					cb.SetCurrentIndex(int(card.Doors[i]))
				}
			}
			a.logf("Card %d: valid %s to %s, doors %v", n, card.From.Format("2006-01-02"), card.To.Format("2006-01-02"), card.Doors)
		}, nil
	})
}

func (a *App) putCard() {
	n, ok := a.cardNumberValue()
	if !ok {
		return
	}
	card := uhppote.Card{Number: n, From: a.cardFrom.Date(), To: a.cardTo.Date()}
	if card.To.Format("20060102") < card.From.Format("20060102") {
		a.warn("Card", "'Valid to' is before 'Valid from'.")
		return
	}
	for i, cb := range a.cardDoors {
		card.Doors[i] = byte(cb.CurrentIndex())
	}
	a.run(fmt.Sprintf("Add / update card %d", n), func(c *uhppote.Controller) (func(), error) {
		if err := result(c.PutCard(card)); err != nil {
			return nil, err
		}
		return a.readCardCount, nil
	})
}

func (a *App) deleteCard() {
	n, ok := a.cardNumberValue()
	if !ok {
		return
	}
	if !a.confirm("Delete card", fmt.Sprintf("Delete card %d from the controller?", n)) {
		return
	}
	a.run(fmt.Sprintf("Delete card %d", n), func(c *uhppote.Controller) (func(), error) {
		if err := result(c.DeleteCard(n)); err != nil {
			return nil, err
		}
		return a.readCardCount, nil
	})
}

func (a *App) deleteAllCards() {
	if !a.confirm("Delete ALL cards", fmt.Sprintf(
		"This removes EVERY card from controller %d.\nNobody will be able to badge in until cards are added again.\n\nContinue?",
		a.ctrl.Serial)) {
		return
	}
	a.run("Delete all cards", func(c *uhppote.Controller) (func(), error) {
		if err := result(c.DeleteAllCards()); err != nil {
			return nil, err
		}
		return a.readCardCount, nil
	})
}

// ── Events ────────────────────────────────────────────────────────────────

func (a *App) eventsPage() TabPage {
	return TabPage{
		Title:  "Events",
		Layout: VBox{},
		Children: []Widget{
			Composite{
				Layout: HBox{MarginsZero: true},
				Children: []Widget{
					Label{Text: "Newest event:"},
					Label{AssignTo: &a.evNewest, Text: "-", Font: Font{Bold: true}},
					Label{Text: "   Marked as read up to:"},
					Label{AssignTo: &a.evRead, Text: "-", Font: Font{Bold: true}},
					PushButton{Text: "Refresh", OnClicked: a.readEventIndexes},
					HSpacer{},
				},
			},
			Composite{
				Layout: HBox{MarginsZero: true},
				Children: []Widget{
					Label{Text: "Show last"},
					NumberEdit{AssignTo: &a.evCount, MinValue: 1, MaxValue: maxEventFetch, Value: 20.0, SpinButtonsVisible: true, MaxSize: Size{Width: 70}},
					Label{Text: "events"},
					PushButton{Text: "Fetch", OnClicked: func() { a.fetchEvents(false) }},
					PushButton{Text: "Fetch unread", OnClicked: func() { a.fetchEvents(true) }},
					PushButton{Text: "Mark all as read", OnClicked: a.markEventsRead},
					HSpacer{},
				},
			},
			TableView{
				Model:                    a.evModel,
				AlternatingRowBG:         true,
				NotSortableByHeaderClick: true,
				LastColumnStretched:      true,
				Columns: []TableViewColumn{
					{Title: "#", Width: 60},
					{Title: "Time", Width: 135},
					{Title: "Card", Width: 85},
					{Title: "Door", Width: 45},
					{Title: "Dir", Width: 40},
					{Title: "Access", Width: 65},
					{Title: "Type", Width: 95},
					{Title: "Reason"},
				},
			},
		},
	}
}

func (a *App) readEventIndexes() {
	a.run("Read event indexes", func(c *uhppote.Controller) (func(), error) {
		st, err := c.Status()
		if err != nil {
			return nil, err
		}
		read, err := c.GetEventIndex()
		if err != nil {
			return nil, err
		}
		return func() {
			a.evNewest.SetText(fmt.Sprint(st.LastEvent.Index))
			a.evRead.SetText(fmt.Sprint(read))
		}, nil
	})
}

// fetchEvents loads events newest-first: either the last N, or everything
// after the "read" index.
func (a *App) fetchEvents(unreadOnly bool) {
	if !a.evFetchOn.CompareAndSwap(false, true) {
		a.setStatus("Already fetching events…")
		return
	}
	want := uint32(a.evCount.Value())
	desc := fmt.Sprintf("Fetch last %d events", want)
	if unreadOnly {
		desc = "Fetch unread events"
	}
	a.run(desc, func(c *uhppote.Controller) (func(), error) {
		defer a.evFetchOn.Store(false)
		st, err := c.Status()
		if err != nil {
			return nil, err
		}
		newest := st.LastEvent.Index
		oldest := uint32(1)
		if unreadOnly {
			read, err := c.GetEventIndex()
			if err != nil {
				return nil, err
			}
			oldest = read + 1
		} else if newest > want {
			oldest = newest - want + 1
		}
		if newest >= oldest && newest-oldest+1 > maxEventFetch {
			oldest = newest - maxEventFetch + 1
		}

		var rows [][]string
		for i := newest; i >= oldest && i > 0; i-- {
			e, err := c.GetEvent(i)
			if err != nil {
				return nil, err
			}
			rows = append(rows, []string{
				fmt.Sprint(e.Index),
				fmtTime(e.Timestamp, e.HasTime),
				fmt.Sprint(e.Card),
				fmt.Sprint(e.Door),
				e.DirectionText(),
				yesNo(e.Granted, "Granted", "Denied"),
				e.TypeText(),
				e.ReasonText(),
			})
			if n := len(rows); n%10 == 0 {
				a.ui(func() { a.setStatus("%s… %d", desc, n) })
			}
		}
		return func() {
			a.evModel.set(rows)
			a.evNewest.SetText(fmt.Sprint(newest))
			if len(rows) == 0 {
				a.logf("No events to show.")
			}
		}, nil
	})
	if a.ctrl == nil {
		a.evFetchOn.Store(false)
	}
}

func (a *App) markEventsRead() {
	a.run("Mark all events as read", func(c *uhppote.Controller) (func(), error) {
		st, err := c.Status()
		if err != nil {
			return nil, err
		}
		if err := result(c.SetEventIndex(st.LastEvent.Index)); err != nil {
			return nil, err
		}
		return a.readEventIndexes, nil
	})
}

// ── Settings ──────────────────────────────────────────────────────────────

func (a *App) settingsPage() TabPage {
	return TabPage{
		Title:  "Settings",
		Layout: VBox{},
		Children: []Widget{
			GroupBox{
				Title:  "Clock",
				Layout: Grid{Columns: 4},
				Children: []Widget{
					Label{Text: "Controller time:"},
					Label{AssignTo: &a.ctrlTime, Text: "-", Font: Font{Bold: true}},
					PushButton{Text: "Read", OnClicked: a.readTime},
					PushButton{Text: "Sync to this PC's time", OnClicked: a.syncTime},
					Label{Text: "PC time:"},
					Label{AssignTo: &a.pcTime, Text: "-"},
				},
			},
			GroupBox{
				Title:  "Event listener (where the controller pushes live events)",
				Layout: Grid{Columns: 6},
				Children: []Widget{
					Label{Text: "IP address:"},
					LineEdit{AssignTo: &a.lsnIP},
					Label{Text: "Port:"},
					NumberEdit{AssignTo: &a.lsnPort, MinValue: 0, MaxValue: 65535},
					Label{Text: "Status interval:"},
					NumberEdit{AssignTo: &a.lsnIntv, MinValue: 0, MaxValue: 255, Suffix: " s"},
					PushButton{Text: "Read", OnClicked: a.readListener},
					PushButton{Text: "Save", OnClicked: a.writeListener},
					PushButton{Text: "Use this PC", OnClicked: a.listenerThisPC},
					HSpacer{ColumnSpan: 3},
				},
			},
			note("Interval 0 = send only when something happens (card swipe, door opened, button pressed)."),
			GroupBox{
				Title:  "Network",
				Layout: Grid{Columns: 2},
				Children: []Widget{
					Label{Text: "IP address:"},
					LineEdit{AssignTo: &a.netIP},
					Label{Text: "Subnet mask:"},
					LineEdit{AssignTo: &a.netMask},
					Label{Text: "Gateway:"},
					LineEdit{AssignTo: &a.netGateway},
					PushButton{Text: "Apply…", OnClicked: a.writeNetwork},
				},
			},
			note("The controller does not reply to a network change. Search again afterwards to confirm the new address."),
			VSpacer{},
		},
	}
}

func (a *App) readTime() {
	a.run("Read controller time", func(c *uhppote.Controller) (func(), error) {
		t, err := c.GetTime()
		if err != nil {
			return nil, err
		}
		return func() { a.showTime(t) }, nil
	})
}

func (a *App) showTime(t time.Time) {
	now := time.Now()
	a.ctrlTime.SetText(t.Format("2006-01-02 15:04:05"))
	drift := t.Sub(now.Truncate(time.Second)).Round(time.Second)
	a.pcTime.SetText(fmt.Sprintf("%s   (controller is %s %s)",
		now.Format("2006-01-02 15:04:05"), drift.Abs(), yesNo(drift >= 0, "ahead", "behind")))
}

func (a *App) syncTime() {
	a.run("Sync controller clock to PC", func(c *uhppote.Controller) (func(), error) {
		t, err := c.SetTime(time.Now())
		if err != nil {
			return nil, err
		}
		return func() { a.showTime(t) }, nil
	})
}

func (a *App) readListener() {
	a.run("Read event listener", func(c *uhppote.Controller) (func(), error) {
		l, err := c.GetListener()
		if err != nil {
			return nil, err
		}
		return func() {
			a.lsnIP.SetText(l.IP.String())
			a.lsnPort.SetValue(float64(l.Port))
			a.lsnIntv.SetValue(float64(l.Interval))
		}, nil
	})
}

func (a *App) writeListener() {
	ip, err := parseIPv4(a.lsnIP.Text())
	if err != nil {
		a.warn("Event listener", err.Error())
		return
	}
	l := uhppote.Listener{IP: ip, Port: uint16(a.lsnPort.Value()), Interval: byte(a.lsnIntv.Value())}
	a.run(fmt.Sprintf("Set event listener to %s:%d", l.IP, l.Port), func(c *uhppote.Controller) (func(), error) {
		return nil, result(c.SetListener(l))
	})
}

func (a *App) listenerThisPC() {
	if a.ctrl == nil {
		return
	}
	ip := localIPFor(a.ctrl.Addr.IP)
	if ip == nil {
		a.warn("Event listener", "Could not work out this PC's address on the controller's network.")
		return
	}
	a.lsnIP.SetText(ip.String())
	if a.lsnPort.Value() == 0 {
		a.lsnPort.SetValue(60001)
	}
	a.setStatus("Filled in %s. Click Save to send it to the controller.", ip)
}

func (a *App) writeNetwork() {
	ip, err1 := parseIPv4(a.netIP.Text())
	mask, err2 := parseIPv4(a.netMask.Text())
	gw, err3 := parseIPv4(a.netGateway.Text())
	for _, err := range []error{err1, err2, err3} {
		if err != nil {
			a.warn("Network", err.Error())
			return
		}
	}
	if !a.confirm("Change controller network settings", fmt.Sprintf(
		"Change controller %d to:\n\n  IP address:  %s\n  Subnet mask: %s\n  Gateway:     %s\n\n"+
			"If these are wrong you may lose contact with the controller until it is on a reachable network.\n\nContinue?",
		a.ctrl.Serial, ip, mask, gw)) {
		return
	}
	a.run(fmt.Sprintf("Change controller IP to %s", ip), func(c *uhppote.Controller) (func(), error) {
		if err := c.SetAddress(ip, mask, gw); err != nil {
			return nil, err
		}
		return func() {
			a.logf("Network settings sent. Searching again to confirm…")
			a.setConnected(nil, nil)
			a.searchAddr.SetText("")
			time.AfterFunc(2*time.Second, func() { a.ui(a.search) })
		}, nil
	})
}

// initDefaults sets initial values that declarative fields cannot express.
func (a *App) initDefaults() {
	today := time.Now()
	a.cardFrom.SetDate(today)
	a.cardTo.SetDate(time.Date(today.Year()+1, 12, 31, 0, 0, 0, 0, time.Local))
	a.delayEdit.SetValue(3)
}
