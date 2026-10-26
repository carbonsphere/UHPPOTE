//go:build windows

// UHPPOTE Manager is a Windows desktop tool for finding UHPPOTE access
// controllers on the network, connecting to one, sending commands and
// watching its status.
package main

import (
	"fmt"
	"log"
	"net"
	"strings"
	"time"

	"github.com/tailscale/walk"
	. "github.com/tailscale/walk/declarative"

	"github.com/carbonsphere/UHPPOTE/Windows/uhppote"
)

const searchTimeout = 2 * time.Second

func main() {
	app, err := walk.InitApp()
	if err != nil {
		log.Fatal(err)
	}

	a := &App{
		devModel:   &rowsModel{},
		doorsModel: &rowsModel{},
		infoModel:  &rowsModel{},
		evModel:    &rowsModel{},
	}

	err = MainWindow{
		AssignTo: &a.mw,
		Title:    "UHPPOTE Manager",
		Size:     Size{Width: 1150, Height: 780},
		MinSize:  Size{Width: 900, Height: 600},
		Layout:   VBox{},
		Children: []Widget{
			Composite{
				Layout: HBox{MarginsZero: true},
				Children: []Widget{
					Label{Text: "Search:"},
					LineEdit{
						AssignTo:  &a.searchAddr,
						CueBanner: "Leave blank to search all networks, or type a controller IP",
						MaxSize:   Size{Width: 380},
						OnKeyDown: func(key walk.Key) {
							if key == walk.KeyReturn {
								a.search()
							}
						},
					},
					PushButton{AssignTo: &a.searchBtn, Text: "Search", OnClicked: a.search},
					HSpacer{},
					Label{AssignTo: &a.connLabel, Text: "Not connected", Font: Font{Bold: true}},
				},
			},
			VSplitter{
				Children: []Widget{
					HSplitter{
						StretchFactor: 3,
						Children: []Widget{
							a.devicePanel(),
							TabWidget{
								AssignTo:      &a.tabs,
								StretchFactor: 2,
								Pages: []TabPage{
									a.statusPage(),
									a.doorsPage(),
									a.cardsPage(),
									a.eventsPage(),
									a.settingsPage(),
								},
							},
						},
					},
					a.logPanel(),
				},
			},
		},
		StatusBarItems: []StatusBarItem{
			{AssignTo: &a.statusBar, Text: "Ready."},
		},
	}.Create()
	if err != nil {
		log.Fatal(err)
	}

	if icon, err := walk.NewIconFromResource("APP"); err == nil {
		a.mw.SetIcon(icon)
	}
	a.setConnected(nil, nil)
	a.initDefaults()
	a.mw.Closing().Attach(func(*bool, walk.CloseReason) { a.setAutoRefresh(false) })
	a.search()
	app.Run()
}

func (a *App) devicePanel() Widget {
	return Composite{
		StretchFactor: 1,
		Layout:        VBox{MarginsZero: true},
		Children: []Widget{
			Label{Text: "Controllers found", Font: Font{Bold: true}},
			TableView{
				AssignTo:                 &a.devTable,
				Model:                    a.devModel,
				AlternatingRowBG:         true,
				NotSortableByHeaderClick: true,
				LastColumnStretched:      true,
				Columns: []TableViewColumn{
					{Title: "", Width: 24},
					{Title: "Serial", Width: 90},
					{Title: "IP address", Width: 110},
					{Title: "Firmware", Width: 65},
					{Title: "MAC"},
				},
				OnItemActivated: a.connectSelected,
			},
			Composite{
				Layout: HBox{MarginsZero: true},
				Children: []Widget{
					PushButton{AssignTo: &a.connectBtn, Text: "Connect", OnClicked: a.connectSelected},
					PushButton{AssignTo: &a.discBtn, Text: "Disconnect", OnClicked: func() { a.setConnected(nil, nil) }},
					HSpacer{},
				},
			},
		},
	}
}

func (a *App) logPanel() Widget {
	return Composite{
		StretchFactor: 1,
		Layout:        VBox{MarginsZero: true},
		Children: []Widget{
			Composite{
				Layout: HBox{MarginsZero: true},
				Children: []Widget{
					Label{Text: "Log", Font: Font{Bold: true}},
					CheckBox{AssignTo: &a.rawLog, Text: "Show raw packets (hex)"},
					HSpacer{},
					PushButton{Text: "Clear", OnClicked: func() {
						a.logView.SetText("")
						a.logChars = 0
					}},
				},
			},
			TextEdit{
				AssignTo: &a.logView,
				ReadOnly: true,
				VScroll:  true,
				Font:     Font{Family: "Consolas", PointSize: 9},
			},
		},
	}
}

// search looks for controllers and fills the device table.
func (a *App) search() {
	var target net.IP
	if s := strings.TrimSpace(a.searchAddr.Text()); s != "" {
		ip, err := parseIPv4(s)
		if err != nil {
			a.warn("Search", err.Error())
			return
		}
		target = ip
	}

	a.searchBtn.SetEnabled(false)
	where := "all networks"
	if target != nil {
		where = target.String()
	}
	a.setStatus("Searching %s…", where)
	a.logf("Searching %s…", where)

	go func() {
		found, err := uhppote.Search(target, searchTimeout, a.packetLog)
		a.ui(func() {
			a.searchBtn.SetEnabled(true)
			if err != nil {
				a.logf("✖ Search failed: %v", err)
				a.setStatus("Search failed: %v", err)
				return
			}
			a.devices = found
			a.refreshDeviceTable()
			for _, d := range found {
				a.logf("Found controller %d at %s (firmware %s, MAC %s)", d.Serial, d.IP, d.Firmware, d.MAC)
			}
			a.setStatus("Found %d controller(s). Select one and click Connect (or double-click it).", len(found))
			if len(found) > 0 && a.devTable.CurrentIndex() < 0 {
				a.devTable.SetCurrentIndex(0)
			}
		})
	}()
}

func (a *App) refreshDeviceTable() {
	rows := make([][]string, len(a.devices))
	for i, d := range a.devices {
		mark := ""
		if a.device != nil && a.device.Serial == d.Serial {
			mark = "●"
		}
		rows[i] = []string{mark, fmt.Sprint(d.Serial), d.IP.String(), d.Firmware, strings.ToUpper(d.MAC.String())}
	}
	cur := a.devTable.CurrentIndex()
	a.devModel.set(rows)
	if cur >= 0 && cur < len(rows) {
		a.devTable.SetCurrentIndex(cur)
	}
}

// connectSelected checks the selected controller answers, then makes it the
// active controller.
func (a *App) connectSelected() {
	i := a.devTable.CurrentIndex()
	if i < 0 || i >= len(a.devices) {
		a.setStatus("Select a controller in the list first.")
		return
	}
	d := a.devices[i]
	c := uhppote.NewController(d.Serial, d.IP, a.packetLog)

	a.connectBtn.SetEnabled(false)
	a.setStatus("Connecting to %d at %s…", d.Serial, d.IP)
	go func() {
		st, err := c.Status()
		a.ui(func() {
			a.connectBtn.SetEnabled(true)
			if err != nil {
				a.logf("✖ Connect to %d failed: %v", d.Serial, err)
				a.setStatus("Could not connect: %v", err)
				a.warn("Connect", fmt.Sprintf("Controller %d at %s did not answer.\n\n%v", d.Serial, d.IP, err))
				return
			}
			a.setConnected(c, d)
			a.showStatus(st)
			a.logf("Connected to controller %d at %s", d.Serial, d.IP)
			a.setStatus("Connected to %d.", d.Serial)
			a.readDoorControl()
			a.readEventIndexes()
			a.readListener()
		})
	}()
}

// setConnected switches the active controller (nil to disconnect).
func (a *App) setConnected(c *uhppote.Controller, d *uhppote.Device) {
	a.setAutoRefresh(false)
	if a.autoRefresh != nil {
		a.autoRefresh.SetChecked(false)
	}
	a.ctrl, a.device = c, d
	connected := c != nil
	a.tabs.SetEnabled(connected)
	a.discBtn.SetEnabled(connected)
	if connected {
		a.connLabel.SetText(fmt.Sprintf("Connected: %d @ %s  (firmware %s)", d.Serial, d.IP, d.Firmware))
		a.netIP.SetText(d.IP.String())
		a.netMask.SetText(d.Mask.String())
		a.netGateway.SetText(d.Gateway.String())
	} else {
		a.connLabel.SetText("Not connected")
		a.doorsModel.set(nil)
		a.infoModel.set(nil)
		a.evModel.set(nil)
	}
	a.refreshDeviceTable()
}
