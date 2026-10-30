//go:build windows

package main

import (
	_ "embed"
	"log"
	"os"
	"os/exec"
	"path/filepath"
	"syscall"
	"unsafe"
)

// The Evergreen bootstrapper is embedded so a fresh Windows install needs
// nothing from the player: when WebView2 is missing, the launcher installs it
// per-user and silently, then starts normally.
//
//go:embed webview2/MicrosoftEdgeWebView2Setup.exe
var webview2Setup []byte

var (
	user32          = syscall.NewLazyDLL("user32.dll")
	procMessageBoxW = user32.NewProc("MessageBoxW")
)

const (
	webview2ClientGUID = "{F3017226-FE2A-4295-8BDF-00C3A9A4C4E6}"
	mbIconError        = 0x00000010
	mbOK               = 0x00000000
)

// ensureWebView2 makes sure the Edge WebView2 runtime is available.
func ensureWebView2() error {
	if webview2Installed() {
		return nil
	}
	log.Println("WebView2 runtime missing, installing the embedded bootstrapper")
	dir, err := os.MkdirTemp("", "valkaw-webview2")
	if err != nil {
		return err
	}
	defer os.RemoveAll(dir)
	setup := filepath.Join(dir, "MicrosoftEdgeWebView2Setup.exe")
	if err := os.WriteFile(setup, webview2Setup, 0755); err != nil {
		return err
	}
	cmd := exec.Command(setup, "/silent", "/install")
	cmd.SysProcAttr = &syscall.SysProcAttr{HideWindow: true}
	if out, err := cmd.CombinedOutput(); err != nil {
		log.Printf("WebView2 installer failed: %v %s", err, out)
		return err
	}
	if !webview2Installed() {
		return os.ErrNotExist
	}
	return nil
}

// webview2Installed checks the registration keys and the install folders.
func webview2Installed() bool {
	for _, key := range []string{
		`HKLM\SOFTWARE\WOW6432Node\Microsoft\EdgeUpdate\Clients\` + webview2ClientGUID,
		`HKLM\SOFTWARE\Microsoft\EdgeUpdate\Clients\` + webview2ClientGUID,
		`HKCU\Software\Microsoft\EdgeUpdate\Clients\` + webview2ClientGUID,
	} {
		cmd := exec.Command("reg", "query", key, "/v", "pv")
		cmd.SysProcAttr = &syscall.SysProcAttr{HideWindow: true}
		if out, err := cmd.Output(); err == nil && len(out) > 0 {
			return true
		}
	}
	for _, base := range []string{os.Getenv("ProgramFiles(x86)"), os.Getenv("ProgramFiles"), os.Getenv("LOCALAPPDATA")} {
		if base == "" {
			continue
		}
		matches, _ := filepath.Glob(filepath.Join(base, "Microsoft", "EdgeWebView2", "Application", "*", "msedgewebview2.exe"))
		if len(matches) > 0 {
			return true
		}
	}
	return false
}

// messageBox shows a blocking dialog; used when the launcher cannot start.
func messageBox(text, title string) {
	t, _ := syscall.UTF16PtrFromString(text)
	c, _ := syscall.UTF16PtrFromString(title)
	procMessageBoxW.Call(0, uintptr(unsafe.Pointer(t)), uintptr(unsafe.Pointer(c)), mbIconError|mbOK)
}
