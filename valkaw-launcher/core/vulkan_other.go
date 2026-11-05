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

var vkVersionRe = regexp.MustCompile(`(\d+)\.(\d+)\.(\d+)`)

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
	var rawDriver string
	flush := func() {
		if dev.Name != "" || dev.APIVersion != 0 {
			dev.DriverVersion = encodeDriverVersion(rawDriver, dev.VendorID)
			p.Devices = append(p.Devices, dev)
		}
		dev = vulkanDevice{}
		rawDriver = ""
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
			dev.APIVersion = parseVersion3(value)
		case "driverVersion":
			rawDriver = value
		case "vendorID":
			dev.VendorID = parseNumber(value)
		case "deviceType":
			dev.Type = deviceTypeValue(value)
		}
	}
	flush()
	return p
}

// parseVersion3 reads a x.y.z version from text the way vulkaninfo prints it
// ("1.4.351" or "1.4.351(29)"), without requiring parentheses.
func parseVersion3(s string) uint32 {
	if m := vkVersionRe.FindStringSubmatch(s); m != nil {
		return vkVersion(atoi(m[1]), atoi(m[2]), atoi(m[3]))
	}
	return 0
}

// encodeDriverVersion packs a printed driver version back into the
// vendor-specific 32-bit form driverVersionText decodes. vulkaninfo prints the
// version already decoded ("615.78.8.0" for NVIDIA), so the raw text is
// re-packed per vendor to round-trip through the display helpers.
func encodeDriverVersion(s string, vendorID uint32) uint32 {
	parts := strings.Split(s, ".")
	nums := make([]int, 0, 4)
	for _, p := range parts {
		nums = append(nums, atoi(p))
	}
	switch vendorID {
	case 0x10de: // NVIDIA: major.minor.build.revision
		if len(nums) >= 4 {
			return uint32(nums[0]<<22 | nums[1]<<14 | nums[2]<<6 | nums[3])
		}
	case 0x8086: // Intel: major.minor
		if len(nums) >= 2 {
			return uint32(nums[0]<<14 | nums[1])
		}
	}
	if len(nums) >= 3 {
		return uint32(nums[0]<<22 | nums[1]<<12 | nums[2])
	}
	return 0
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
