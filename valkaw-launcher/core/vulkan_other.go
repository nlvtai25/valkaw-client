//go:build !windows

package core

import (
	"context"
	"os/exec"
	"regexp"
	"strconv"
	"strings"
	"time"
)

var vkSummaryVersionRe = regexp.MustCompile(`\((\d+)\.(\d+)\.(\d+)\)`)

// probeVulkan checks the Vulkan ICD files and, when vulkaninfo is installed,
// reads the real device version from it.
func probeVulkan() vulkanProbe {
	p := vulkanProbe{Loader: vulkanReady()}
	if !p.Loader {
		return p
	}
	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()
	out, err := exec.CommandContext(ctx, "vulkaninfo", "--summary").Output()
	if err != nil {
		return p // runtime present, version unknown
	}
	p.Verified = true
	var dev vulkanDevice
	flush := func() {
		if dev.Name != "" || dev.APIVersion != 0 {
			p.Devices = append(p.Devices, dev)
		}
		dev = vulkanDevice{}
	}
	for _, raw := range strings.Split(string(out), "\n") {
		line := strings.TrimSpace(raw)
		if strings.HasPrefix(line, "GPU") && strings.HasSuffix(line, ":") {
			flush()
			continue
		}
		key, value, ok := strings.Cut(line, "=")
		if !ok {
			continue
		}
		value = strings.TrimSpace(value)
		switch strings.TrimSpace(key) {
		case "deviceName":
			dev.Name = value
		case "apiVersion":
			if m := vkSummaryVersionRe.FindStringSubmatch(value); m != nil {
				dev.APIVersion = vkVersion(atoi(m[1]), atoi(m[2]), atoi(m[3]))
			}
		case "driverVersion":
			dev.DriverVersion = parseNumber(value)
		case "vendorID":
			dev.VendorID = parseNumber(value)
		case "deviceType":
			dev.Type = deviceTypeValue(value)
		}
	}
	flush()
	return p
}

func vkVersion(major, minor, patch int) uint32 {
	return uint32(major<<22 | minor<<12 | patch)
}

func atoi(s string) int {
	n, _ := strconv.Atoi(s)
	return n
}

func parseNumber(s string) uint32 {
	s = strings.Fields(strings.TrimSpace(s))[0]
	n, err := strconv.ParseUint(s, 0, 32)
	if err != nil {
		return 0
	}
	return uint32(n)
}

func deviceTypeValue(s string) uint32 {
	switch {
	case strings.Contains(s, "DISCRETE"):
		return 2
	case strings.Contains(s, "INTEGRATED"):
		return 1
	case strings.Contains(s, "VIRTUAL"):
		return 3
	case strings.Contains(s, "CPU"):
		return 4
	}
	return 0
}
