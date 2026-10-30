package core

import (
	"fmt"
	"regexp"
)

// VulkanMod needs Vulkan 1.3. Anything older cannot start the Vulkan profile.
const (
	vulkanMinMajor = 1
	vulkanMinMinor = 3
)

// VulkanCheck tells the frontend whether the Vulkan profile can run here and,
// when it cannot, why and what to do about it.
type VulkanCheck struct {
	Supported     bool   `json:"supported"`
	Loader        bool   `json:"loader"`
	Verified      bool   `json:"verified"`
	Device        string `json:"device"`
	APIVersion    string `json:"apiVersion"`
	DriverVersion string `json:"driverVersion"`
	Reason        string `json:"reason"`
	Fix           string `json:"fix"` // "install-driver" | "update-driver" | "upgrade-gpu" | ""
	FixText       string `json:"fixText"`
	DriverURL     string `json:"driverUrl"`
	Summary       string `json:"summary"`
}

// vulkanDevice is one physical GPU reported by the Vulkan driver.
type vulkanDevice struct {
	Name          string
	APIVersion    uint32
	DriverVersion uint32
	VendorID      uint32
	DeviceID      uint32
	Type          uint32 // 0 other, 1 integrated, 2 discrete, 3 virtual, 4 cpu
}

// vulkanProbe is the platform-specific result. Verified is false when the
// runtime exists but the device details could not be read.
type vulkanProbe struct {
	Loader        bool
	LoaderVersion uint32
	Verified      bool
	Devices       []vulkanDevice
	Err           string
}

// CheckVulkan reports the Vulkan capability of this machine.
func (a *App) CheckVulkan() VulkanCheck { return checkVulkan() }

func checkVulkan() VulkanCheck {
	p := probeVulkan()
	c := VulkanCheck{Loader: p.Loader, Verified: p.Verified}
	if !p.Loader {
		c.Reason = "No Vulkan runtime found. VulkanMod needs a GPU driver that ships Vulkan 1.3."
		return withFix(c, "install-driver", 0)
	}
	dev, ok := bestDevice(p.Devices)
	if !ok {
		if !p.Verified {
			// The runtime is there but the version cannot be read; do not block.
			c.Supported = true
			c.Summary = "Vulkan runtime present"
			c.Reason = "Vulkan runtime present. Device details are unavailable on this system."
			return c
		}
		c.Reason = "A Vulkan runtime is installed but it reports no GPU. The driver is incomplete or too old."
		return withFix(c, "update-driver", 0)
	}

	c.Device = dev.Name
	c.APIVersion = versionText(dev.APIVersion)
	c.DriverVersion = driverVersionText(dev.DriverVersion, dev.VendorID)
	c.DriverURL = driverPage(dev.VendorID)

	if versionAtLeast(dev.APIVersion, vulkanMinMajor, vulkanMinMinor) {
		c.Supported = true
		c.Summary = "Vulkan " + c.APIVersion + " • " + dev.Name
		c.Reason = "Vulkan " + c.APIVersion + " is available on " + dev.Name + "."
		return c
	}

	c.Reason = fmt.Sprintf("%s reports Vulkan %s, but VulkanMod requires Vulkan %d.%d.",
		dev.Name, c.APIVersion, vulkanMinMajor, vulkanMinMinor)
	if gpuSupportsVulkan13(dev) {
		return withFix(c, "update-driver", dev.VendorID)
	}
	return withFix(c, "upgrade-gpu", dev.VendorID)
}

func withFix(c VulkanCheck, fix string, vendorID uint32) VulkanCheck {
	c.Fix = fix
	if c.DriverURL == "" {
		c.DriverURL = driverPage(vendorID)
	}
	switch fix {
	case "install-driver":
		c.FixText = "Install the latest GPU driver from the vendor. Vulkan ships inside the driver, nothing else to download."
	case "update-driver":
		c.FixText = "Update the GPU driver to the newest version from the vendor, then reopen the launcher and pick Vulkan again."
	case "upgrade-gpu":
		c.FixText = "No driver update can add Vulkan 1.3 to this GPU. Play with the Sodium (OpenGL) profile, or use a GPU that supports Vulkan 1.3 to run VulkanMod."
	}
	c.Summary = c.Reason
	return c
}

// gpuSupportsVulkan13 reports whether the GPU family can reach Vulkan 1.3 with a
// current driver, so the launcher can tell "driver is old" from "GPU is too old".
func gpuSupportsVulkan13(dev vulkanDevice) bool {
	if dev.Type == 4 {
		return false // software renderer (lavapipe / cpu)
	}
	name := dev.Name
	switch dev.VendorID {
	case 0x10de: // NVIDIA: Maxwell 2 and newer
		return reNvidiaV13.MatchString(name)
	case 0x1002: // AMD: GCN 1 and newer
		return reAmdV13.MatchString(name)
	case 0x8086: // Intel: Gen9 and newer
		return reIntelV13.MatchString(name)
	}
	return false
}

var (
	reNvidiaV13 = regexp.MustCompile(`(?i)\b(GTX ?(7[45]0|9\d0|10\d0|16\d0)|MX ?\d{3}|RTX|TITAN (X|V|RTX|Z)|Quadro (RTX|M|P|T)|Tesla|L4|L40|A\d0)\b`)
	reAmdV13    = regexp.MustCompile(`(?i)\b(RX ?\d{3,4}|Vega|Radeon VII|R[579] ?(2|3|4)\d0|HD 7[789]\d0|Instinct|WX ?\d{4}|Pro W\d{4})\b`)
	reIntelV13  = regexp.MustCompile(`(?i)\b(Arc|Iris Xe|Iris Plus|UHD (6|7|8|9)\d\d|HD (5|6)\d\d)\b`)
)

func driverPage(vendorID uint32) string {
	switch vendorID {
	case 0x10de:
		return "https://www.nvidia.com/Download/index.aspx"
	case 0x1002:
		return "https://www.amd.com/en/support"
	case 0x8086:
		return "https://www.intel.com/content/www/us/en/support/detect.html"
	}
	return "https://www.google.com/search?q=gpu+driver+download"
}

func bestDevice(devices []vulkanDevice) (vulkanDevice, bool) {
	var best vulkanDevice
	found := false
	for _, d := range devices {
		if d.Type == 4 {
			continue
		}
		if !found || d.APIVersion > best.APIVersion || (d.APIVersion == best.APIVersion && d.Type == 2) {
			best, found = d, true
		}
	}
	return best, found
}

func vkVersionParts(v uint32) (int, int) {
	return int(v >> 22), int((v >> 12) & 0x3ff)
}

func versionText(v uint32) string {
	major, minor := vkVersionParts(v)
	return fmt.Sprintf("%d.%d.%d", major, minor, int(v&0xfff))
}

// driverVersionText decodes the vendor-specific packing of driverVersion.
func driverVersionText(v, vendorID uint32) string {
	switch vendorID {
	case 0x10de:
		return fmt.Sprintf("%d.%d.%d.%d", v>>22, (v>>14)&0xff, (v>>6)&0xff, v&0x3f)
	case 0x8086:
		return fmt.Sprintf("%d.%d", v>>14, v&0x3fff)
	}
	return fmt.Sprintf("%d.%d.%d", v>>22, (v>>12)&0x3ff, v&0xfff)
}

func versionAtLeast(v uint32, major, minor int) bool {
	vm, vn := vkVersionParts(v)
	return vm > major || (vm == major && vn >= minor)
}
