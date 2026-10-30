package core

import (
	"net/http"
	"net/http/httptest"
	"runtime"
	"strings"
	"testing"
)

func TestFetchDownloadDir(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if ua := r.Header.Get("User-Agent"); ua == "" {
			t.Error("missing User-Agent")
		}
		w.Write([]byte(`[{"name":"valkaw-launcher-v1.0.3-linux-amd64"},{"name":"README.md"}]`))
	}))
	defer srv.Close()
	old := repoDownloadsAPI
	repoDownloadsAPI = srv.URL
	defer func() { repoDownloadsAPI = old }()

	names, err := (&App{}).fetchDownloadDir()
	if err != nil {
		t.Fatal(err)
	}
	if len(names) != 2 || names[0] != "valkaw-launcher-v1.0.3-linux-amd64" {
		t.Fatalf("names = %v", names)
	}
}

func TestLatestUpdatePicksNewestForPlatform(t *testing.T) {
	names := []string{
		"README.md",
		"valkawclient-1.0.1-sodium.jar",
		"valkaw-launcher-v1.0.2-linux-amd64",
		"valkaw-launcher-v1.0.3-linux-amd64",
		"valkaw-launcher-v1.0.4-linux-amd64",
		"valkaw-launcher-win-v1.0.3-windows-amd64.exe",
		"valkaw-launcher-win-v1.0.4-windows-amd64.exe",
	}
	version, file := latestUpdate(names)
	if version != "v1.0.4" {
		t.Fatalf("version = %q, want v1.0.4", version)
	}
	want := "-" + runtime.GOOS + "-" + runtime.GOARCH
	if !strings.HasSuffix(file, want) && !strings.HasSuffix(file, want+".exe") {
		t.Fatalf("file = %q, want suffix %q", file, want)
	}
}

func TestLatestUpdateIgnoresOtherFiles(t *testing.T) {
	version, file := latestUpdate([]string{"README.md", "CHANGELOG.md"})
	if version != "" || file != "" {
		t.Fatalf("got %q/%q, want empty", version, file)
	}
}

func TestNewerVersion(t *testing.T) {
	cases := []struct {
		latest, current string
		want            bool
	}{
		{"v1.0.4", "v1.0.3", true},
		{"v1.0.3", "v1.0.3", false},
		{"v1.0.2", "v1.0.3", false},
		{"v1.1.0", "v1.0.9", true},
		{"v1.0.3", "", true},
	}
	for _, c := range cases {
		if got := newerVersion(c.latest, c.current); got != c.want {
			t.Errorf("newerVersion(%q, %q) = %v, want %v", c.latest, c.current, got, c.want)
		}
	}
}
