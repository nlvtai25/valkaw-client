package main

import (
	"embed"

	"valkaw-launcher/core"

	"github.com/wailsapp/wails/v2"
	"github.com/wailsapp/wails/v2/pkg/options"
	"github.com/wailsapp/wails/v2/pkg/options/assetserver"
	"github.com/wailsapp/wails/v2/pkg/options/linux"
)

//go:embed all:frontend/dist
var assets embed.FS

// launcherVersion is the Linux build; the Windows shell keeps its own number.
const launcherVersion = "v1.0.6"

func main() {
	app := core.NewApp(launcherVersion)

	err := wails.Run(&options.App{
		Title:  "Valkaw Launcher " + launcherVersion,
		Width:  1024,
		Height: 768,
		AssetServer: &assetserver.Options{
			Assets: assets,
		},
		BackgroundColour: &options.RGBA{R: 10, G: 9, B: 7, A: 1},
		Linux: &linux.Options{
			Icon:        core.AppIcon(),
			ProgramName: "valkaw-launcher",
			Messages:    linux.DefaultMessages(),
		},
		OnStartup: app.Startup,
		Bind: []interface{}{
			app,
		},
	})

	if err != nil {
		println("Error:", err.Error())
	}
}
