package main

import (
	"embed"

	"valkaw-launcher/core"

	"github.com/wailsapp/wails/v2"
	"github.com/wailsapp/wails/v2/pkg/options"
	"github.com/wailsapp/wails/v2/pkg/options/assetserver"
)

//go:embed all:frontend/dist
var assets embed.FS

// launcherVersion is the Linux build; the Windows shell keeps its own number.
const launcherVersion = "v1.0.4"

func main() {
	app := core.NewApp(launcherVersion)

	err := wails.Run(&options.App{
		Title:  "Valkaw Launcher " + launcherVersion,
		Width:  1024,
		Height: 768,
		AssetServer: &assetserver.Options{
			Assets: assets,
		},
		BackgroundColour: &options.RGBA{R: 27, G: 38, B: 54, A: 1},
		OnStartup:        app.Startup,
		Bind: []interface{}{
			app,
		},
	})

	if err != nil {
		println("Error:", err.Error())
	}
}
