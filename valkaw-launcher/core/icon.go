package core

import (
	_ "embed"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
)

//go:embed assets/icon.png
var iconPNG []byte

// AppIcon returns the launcher logo as PNG bytes for the window manager.
func AppIcon() []byte {
	return iconPNG
}

// installDesktopShortcut writes a desktop entry plus icon on Linux so the dock
// and launcher menus group the window under the Valkaw logo instead of showing
// a generic placeholder for an unknown binary.
func installDesktopShortcut() {
	if runtime.GOOS != "linux" {
		return
	}
	home := mustHome()
	exe, err := os.Executable()
	if err != nil {
		return
	}
	iconDir := filepath.Join(home, ".local", "share", "icons", "hicolor", "256x256", "apps")
	appDir := filepath.Join(home, ".local", "share", "applications")
	if os.MkdirAll(iconDir, 0755) != nil || os.MkdirAll(appDir, 0755) != nil {
		return
	}
	iconPath := filepath.Join(iconDir, "valkaw-launcher.png")
	if current, err := os.ReadFile(iconPath); err != nil || len(current) != len(iconPNG) {
		_ = os.WriteFile(iconPath, iconPNG, 0644)
	}
	entry := "[Desktop Entry]\n" +
		"Type=Application\n" +
		"Name=Valkaw Launcher\n" +
		"Comment=Install, update and launch Valkaw Client\n" +
		"Exec=\"" + exe + "\"\n" +
		"Icon=" + iconPath + "\n" +
		"Terminal=false\n" +
		"Categories=Game;\n" +
		"StartupWMClass=valkaw-launcher\n"
	desktop := filepath.Join(appDir, "valkaw-launcher.desktop")
	if current, err := os.ReadFile(desktop); err != nil || string(current) != entry {
		_ = os.WriteFile(desktop, []byte(entry), 0644)
	}
	// The game runs its own window, and desktop shells resolve that window by
	// its class name rather than by the icon the window itself carries.
	gameIcon := filepath.Join(iconDir, "valkaw-client.png")
	if current, err := os.ReadFile(gameIcon); err != nil || len(current) != len(iconPNG) {
		_ = os.WriteFile(gameIcon, iconPNG, 0644)
	}
	gameEntry := "[Desktop Entry]\n" +
		"Type=Application\n" +
		"Name=Valkaw Client\n" +
		"Comment=Minecraft world with the Valkaw client modules\n" +
		"Exec=\"" + exe + "\"\n" +
		"Icon=" + gameIcon + "\n" +
		"Terminal=false\n" +
		"NoDisplay=true\n" +
		"Categories=Game;\n" +
		"StartupWMClass=Minecraft* 1.21.11\n"
	gameDesktop := filepath.Join(appDir, "valkaw-client.desktop")
	if current, err := os.ReadFile(gameDesktop); err != nil || string(current) != gameEntry {
		_ = os.WriteFile(gameDesktop, []byte(gameEntry), 0644)
	}
	// Best effort: refresh the menu cache when the tool is available.
	if bin, err := exec.LookPath("update-desktop-database"); err == nil {
		_ = exec.Command(bin, appDir).Run()
	}
}
