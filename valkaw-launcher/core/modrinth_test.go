package core

import "testing"

func TestSanitizeFileName(t *testing.T) {
	cases := []struct{ in, want string }{
		{"mod-1.0.jar", "mod-1.0.jar"},
		{"../../etc/passwd", "passwd"},
		{`C:\Windows\evil.jar`, "evil.jar"},
		{"..", "download"},
		{".", "download"},
		{"", "download"},
		{"  ", "download"},
	}
	for _, c := range cases {
		if got := sanitizeFileName(c.in); got != c.want {
			t.Errorf("sanitizeFileName(%q) = %q, want %q", c.in, got, c.want)
		}
	}
}

func TestResourceSubdir(t *testing.T) {
	cases := map[string]string{
		"mod":          "mods",
		"resourcepack": "resourcepacks",
		"shader":       "shaderpacks",
		"ShaderPack":   "shaderpacks",
	}
	for in, want := range cases {
		got, err := resourceSubdir(in)
		if err != nil {
			t.Fatalf("resourceSubdir(%q): %v", in, err)
		}
		if got != want {
			t.Errorf("resourceSubdir(%q) = %q, want %q", in, got, want)
		}
	}
	if _, err := resourceSubdir("datapack"); err == nil {
		t.Error("resourceSubdir(datapack) should fail")
	}
}
