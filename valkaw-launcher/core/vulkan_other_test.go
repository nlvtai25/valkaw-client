//go:build !windows

package core

import "testing"

func TestParseVersion3(t *testing.T) {
	cases := []struct {
		in   string
		want uint32
	}{
		{"1.4.351", vkVersion(1, 4, 351)},
		{"1.4.354", vkVersion(1, 4, 354)},
		{"1.4.363(29)", vkVersion(1, 4, 363)},
		{"0.0.0", 0},
		{"", 0},
		{"not a version", 0},
	}
	for _, c := range cases {
		if got := parseVersion3(c.in); got != c.want {
			t.Errorf("parseVersion3(%q) = %d, want %d", c.in, got, c.want)
		}
	}
}

func TestEncodeDriverVersion(t *testing.T) {
	cases := []struct {
		in       string
		vendorID uint32
		want     uint32
	}{
		{"615.78.8.0", 0x10de, 615<<22 | 78<<14 | 8<<6 | 0},
		{"26.2.5", 0x1002, 26<<22 | 2<<12 | 5},
		{"", 0x10de, 0},
	}
	for _, c := range cases {
		if got := encodeDriverVersion(c.in, c.vendorID); got != c.want {
			t.Errorf("encodeDriverVersion(%q, %#x) = %d, want %d", c.in, c.vendorID, got, c.want)
		}
	}
	// Round-trip through the display helpers.
	if got := driverVersionText(encodeDriverVersion("615.78.8.0", 0x10de), 0x10de); got != "615.78.8.0" {
		t.Errorf("NVIDIA driver round-trip = %q, want %q", got, "615.78.8.0")
	}
	if got := driverVersionText(encodeDriverVersion("26.2.5", 0x1002), 0x1002); got != "26.2.5" {
		t.Errorf("AMD driver round-trip = %q, want %q", got, "26.2.5")
	}
}
