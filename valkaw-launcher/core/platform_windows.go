//go:build windows

package core

import (
	"fmt"
	"os/exec"
	"strings"
	"syscall"
)

const gpuPrefKey = `HKCU\Software\Microsoft\DirectX\UserGpuPreferences`

// applyGpuPreference stores the per-program GPU choice Windows keeps for an exe.
// 0 removes the entry, 1 is power saving, 2 is high performance.
func applyGpuPreference(exe string, pref int) error {
	if pref == 0 {
		_ = exec.Command("reg", "delete", gpuPrefKey, "/v", exe, "/f").Run()
		return nil
	}
	value := "GpuPreference=1;"
	if pref == 2 {
		value = "GpuPreference=2;"
	}
	out, err := exec.Command("reg", "add", gpuPrefKey, "/v", exe, "/t", "REG_SZ", "/d", value, "/f").CombinedOutput()
	if err != nil {
		return fmt.Errorf("reg add: %v (%s)", err, strings.TrimSpace(string(out)))
	}
	return nil
}

// hideWindow keeps the updater script from flashing a console window.
func hideWindow(cmd *exec.Cmd) {
	cmd.SysProcAttr = &syscall.SysProcAttr{HideWindow: true}
}
