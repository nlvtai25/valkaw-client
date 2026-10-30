package main

import (
	"embed"
	"log"

	"valkaw-launcher/core"

	"github.com/wailsapp/wails/v2"
	"github.com/wailsapp/wails/v2/pkg/options"
	"github.com/wailsapp/wails/v2/pkg/options/assetserver"
	"github.com/wailsapp/wails/v2/pkg/options/windows"
)

//go:embed all:frontend/dist
var assets embed.FS

// windowsVersion follows the repository release tags, so the Linux and Windows
// launchers update from the same numbers.
const windowsVersion = "v1.0.6.1"

func main() {
	app := core.NewApp(windowsVersion)

	if err := ensureWebView2(); err != nil {
		messageBox(
			"Valkaw Launcher needs the Microsoft Edge WebView2 runtime and could not install it.\n\n"+
				"Install it from https://developer.microsoft.com/microsoft-edge/webview2 and start the launcher again.",
			"Valkaw Launcher")
		return
	}

	err := wails.Run(&options.App{
		Title:            "Valkaw Launcher Windows " + windowsVersion,
		Width:            1024,
		Height:           768,
		AssetServer:      &assetserver.Options{Assets: assets},
		BackgroundColour: &options.RGBA{R: 10, G: 9, B: 7, A: 1},
		OnStartup:        app.Startup,
		Bind:             []interface{}{app},
		Windows: &windows.Options{
			Theme: windows.Dark,
		},
	})

	if err != nil {
		log.Fatal(err)
	}
}
