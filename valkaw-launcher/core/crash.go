package core

import (
	"fmt"
	"os"
	"path/filepath"
	"regexp"
	"strings"
	"time"

	wailsruntime "github.com/wailsapp/wails/v2/pkg/runtime"
)

// crashKeywordRe matches common fatal markers in Minecraft logs so we can point
// the user at roughly where the error is.
var crashKeywordRe = regexp.MustCompile(`(?i)(Exception in thread|Caused by|java\.lang\.|Error|Failed to|Fatal|Could not)`)

// tailFile returns the last maxBytes of a file, with a leading note when it is
// truncated so the user knows the log was cut to keep the popup manageable.
func tailFile(path string, maxBytes int64) string {
	fi, err := os.Stat(path)
	if err != nil {
		return ""
	}
	if fi.IsDir() {
		return ""
	}
	size := fi.Size()
	if size == 0 {
		return "(empty log)"
	}
	offset := int64(0)
	if size > maxBytes {
		offset = size - maxBytes
	}
	f, err := os.Open(path)
	if err != nil {
		return ""
	}
	defer f.Close()
	if offset > 0 {
		if _, err := f.Seek(offset, 0); err != nil {
			return ""
		}
	}
	buf := make([]byte, maxBytes)
	n, err := f.Read(buf)
	if err != nil && n == 0 {
		return ""
	}
	text := string(buf[:n])
	if offset > 0 {
		text = "… (log truncated to last bytes) …\n" + text
	}
	return text
}

// newestCrashReport finds the most recent crash-report file, if the directory
// has a file written within the last two minutes.
func newestCrashReport(gameDir string) string {
	dir := filepath.Join(gameDir, "crash-reports")
	entries, err := os.ReadDir(dir)
	if err != nil {
		return ""
	}
	cutoff := time.Now().Add(-2 * time.Minute)
	var newest string
	var newestMod time.Time
	for _, e := range entries {
		if e.IsDir() {
			continue
		}
		info, err := e.Info()
		if err != nil {
			continue
		}
		if info.ModTime().After(newestMod) {
			newestMod = info.ModTime()
			newest = filepath.Join(dir, e.Name())
		}
	}
	if newest != "" && newestMod.After(cutoff) {
		return newest
	}
	return ""
}

// describeCrash scans a log excerpt and returns a short pointer to the first
// suspicious line, e.g. "latest.log:412 → java.lang.NullPointerException".
func describeCrash(logText, logPath string) string {
	lines := strings.Split(logText, "\n")
	for i, line := range lines {
		if crashKeywordRe.MatchString(line) {
			trimmed := strings.TrimSpace(strings.ReplaceAll(line, "\t", " "))
			if len(trimmed) > 160 {
				trimmed = trimmed[:160] + "…"
			}
			return fmt.Sprintf("%s:%d → %s", shortFileName(logPath), i+1, trimmed)
		}
	}
	return "The game exited unexpectedly. Check the log below."
}

func shortFileName(p string) string {
	if p == "" {
		return "log"
	}
	return filepath.Base(p)
}

// handleGameExit is called from both launch paths after the game process exits.
// If the exit was non-zero or a fresh crash-report exists, it gathers the log
// and emits a "game:crash" event for the frontend to surface in a popup.
func (a *App) handleGameExit(cmdErr error, gameDir string) {
	crashReport := newestCrashReport(gameDir)
	crashed := cmdErr != nil || crashReport != ""
	if !crashed {
		return
	}
	logPath := filepath.Join(gameDir, "logs", "latest.log")
	logText := tailFile(logPath, 200*1024)
	if logText == "" && crashReport != "" {
		logText = tailFile(crashReport, 200*1024)
	}
	out := map[string]any{
		"gameDir": gameDir,
		"logPath": logPath,
		"reason":  describeCrash(logText, logPath),
		"log":     logText,
	}
	if crashReport != "" {
		out["crashReport"] = crashReport
	}
	if a.ctx != nil {
		wailsruntime.EventsEmit(a.ctx, "game:crash", out)
	}
}

// OpenFolder opens a path in the system file manager (used to jump to the log
// folder from the crash popup).
func (a *App) OpenFolder(dir string) error {
	if err := os.MkdirAll(dir, 0755); err != nil {
		return err
	}
	return openFolder(dir)
}
