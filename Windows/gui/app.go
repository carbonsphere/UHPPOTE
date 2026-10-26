//go:build windows

package main

import (
	"errors"
	"fmt"
	"net"
	"strings"
	"sync/atomic"
	"time"

	"github.com/tailscale/walk"

	"github.com/carbonsphere/UHPPOTE/Windows/uhppote"
)

// errRefused is reported when the controller answers but rejects a command.
var errRefused = errors.New("controller rejected the command")

// maxLogChars caps the log window; older text is dropped past this size.
const maxLogChars = 400_000

// rowsModel is a table model backed by rows of display strings.
type rowsModel struct {
	walk.TableModelBase
	rows [][]string
}

func (m *rowsModel) RowCount() int { return len(m.rows) }

func (m *rowsModel) Value(row, col int) interface{} {
	if row < len(m.rows) && col < len(m.rows[row]) {
		return m.rows[row][col]
	}
	return ""
}

func (m *rowsModel) set(rows [][]string) {
	m.rows = rows
	m.PublishRowsReset()
}

// App holds the main window and the currently connected controller.
type App struct {
	mw        *walk.MainWindow
	statusBar *walk.StatusBarItem

	// Discovery
	searchAddr *walk.LineEdit
	searchBtn  *walk.PushButton
	devTable   *walk.TableView
	devModel   *rowsModel
	devices    []*uhppote.Device
	connectBtn *walk.PushButton
	discBtn    *walk.PushButton

	// Connected controller panel
	connLabel *walk.Label
	tabs      *walk.TabWidget
	ctrl      *uhppote.Controller
	device    *uhppote.Device

	// Status tab
	doorsModel  *rowsModel
	infoModel   *rowsModel
	autoRefresh *walk.CheckBox
	stopAuto    chan struct{}
	refreshing  atomic.Bool

	// Doors tab
	doorSel      *walk.ComboBox
	modeSel      *walk.ComboBox
	delayEdit    *walk.NumberEdit
	interlockSel *walk.ComboBox
	keypads      [4]*walk.CheckBox

	// Cards tab
	cardCount  *walk.Label
	cardNumber *walk.LineEdit
	cardFrom   *walk.DateEdit
	cardTo     *walk.DateEdit
	cardDoors  [4]*walk.ComboBox

	// Events tab
	evNewest  *walk.Label
	evRead    *walk.Label
	evCount   *walk.NumberEdit
	evModel   *rowsModel
	evFetchOn atomic.Bool

	// Settings tab
	ctrlTime   *walk.Label
	pcTime     *walk.Label
	lsnIP      *walk.LineEdit
	lsnPort    *walk.NumberEdit
	lsnIntv    *walk.NumberEdit
	netIP      *walk.LineEdit
	netMask    *walk.LineEdit
	netGateway *walk.LineEdit

	// Log
	logView  *walk.TextEdit
	rawLog   *walk.CheckBox
	logChars int
}

// ui runs f on the GUI thread.
func (a *App) ui(f func()) { a.mw.Synchronize(f) }

func (a *App) setStatus(format string, args ...interface{}) {
	if a.statusBar != nil {
		a.statusBar.SetText(fmt.Sprintf(format, args...))
	}
}

// logf appends a timestamped line to the log. Must run on the GUI thread.
func (a *App) logf(format string, args ...interface{}) {
	line := time.Now().Format("15:04:05.000") + "  " + fmt.Sprintf(format, args...) + "\r\n"
	if a.logChars+len(line) > maxLogChars {
		a.logView.SetText("")
		a.logChars = 0
	}
	a.logView.AppendText(line)
	a.logChars += len(line)
}

// packetLog is passed to the protocol layer; it may be called from any goroutine.
func (a *App) packetLog(dir string, addr *net.UDPAddr, p *uhppote.Packet) {
	a.ui(func() {
		if a.rawLog == nil || !a.rawLog.Checked() {
			return
		}
		arrow := "→"
		if dir == "RX" {
			arrow = "←"
		}
		a.logf("%s %s %-15s %s", dir, arrow, addr.IP, p.Hex())
	})
}

// run executes work against the connected controller on a background
// goroutine. On success the returned done func (if any) runs on the GUI thread.
func (a *App) run(desc string, work func(c *uhppote.Controller) (done func(), err error)) {
	c := a.ctrl
	if c == nil {
		a.setStatus("Connect to a controller first.")
		return
	}
	a.setStatus("%s…", desc)
	go func() {
		done, err := work(c)
		a.ui(func() {
			if err != nil {
				a.logf("✖ %s: %v", desc, err)
				a.setStatus("%s failed: %v", desc, err)
				return
			}
			a.logf("✔ %s", desc)
			a.setStatus("%s: done.", desc)
			if done != nil {
				done()
			}
		})
	}()
}

// result converts a controller success flag into an error.
func result(ok bool, err error) error {
	if err != nil {
		return err
	}
	if !ok {
		return errRefused
	}
	return nil
}

func (a *App) confirm(title, msg string) bool {
	return walk.MsgBox(a.mw, title, msg, walk.MsgBoxYesNo|walk.MsgBoxIconWarning) == walk.DlgCmdYes
}

func (a *App) warn(title, msg string) {
	walk.MsgBox(a.mw, title, msg, walk.MsgBoxOK|walk.MsgBoxIconWarning)
}

func parseIPv4(s string) (net.IP, error) {
	ip := net.ParseIP(strings.TrimSpace(s)).To4()
	if ip == nil {
		return nil, fmt.Errorf("%q is not a valid IPv4 address", strings.TrimSpace(s))
	}
	return ip, nil
}

func yesNo(b bool, yes, no string) string {
	if b {
		return yes
	}
	return no
}

func fmtTime(t time.Time, ok bool) string {
	if !ok {
		return "-"
	}
	return t.Format("2006-01-02 15:04:05")
}

// localIPFor returns this PC's address on the route to ip.
func localIPFor(ip net.IP) net.IP {
	conn, err := net.DialUDP("udp4", nil, &net.UDPAddr{IP: ip, Port: uhppote.Port})
	if err != nil {
		return nil
	}
	defer conn.Close()
	return conn.LocalAddr().(*net.UDPAddr).IP.To4()
}
