package core

import "testing"

func TestRequireJavaForMC(t *testing.T) {
	cases := map[string]int{
		"1.12.2":  8,
		"1.16.5":  8,
		"1.17.1":  17,
		"1.19.4":  17,
		"1.20.4":  17,
		"1.20.5":  21,
		"1.21.1":  21,
		"1.21.11": 21,
		"26.1.2":  25,
		"26.3":    25,
	}
	for mc, want := range cases {
		if got := requireJavaForMC(mc); got != want {
			t.Errorf("requireJavaForMC(%q) = %d, want %d", mc, got, want)
		}
	}
}

func TestSlugify(t *testing.T) {
	cases := map[string]string{
		"My Cool Pack!": "my-cool-pack",
		"  spaces  ":    "spaces",
		"":              "instance",
		"Fabric 1.21":   "fabric-1-21",
	}
	for in, want := range cases {
		if got := slugify(in); got != want {
			t.Errorf("slugify(%q) = %q, want %q", in, got, want)
		}
	}
}

func TestNumericVersionLess(t *testing.T) {
	if !numericVersionLess("21.1.103", "21.1.104") {
		t.Error("21.1.103 should be less than 21.1.104")
	}
	if numericVersionLess("21.1.104", "21.1.104") {
		t.Error("equal versions should not be less")
	}
	if !numericVersionLess("2.0", "10.0") {
		t.Error("2.0 should be less than 10.0")
	}
}
