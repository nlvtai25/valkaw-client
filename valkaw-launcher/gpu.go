package main

import (
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"runtime"
	"strings"
)

// GPU is a graphics adapter found on this machine.
type GPU struct {
	ID      string `json:"id"`      // "10de:25ac"
	Name    string `json:"name"`    // "NVIDIA GeForce RTX 3050 6GB Laptop GPU"
	Vendor  string `json:"vendor"`  // "nvidia" | "amd" | "intel" | "other"
	Address string `json:"address"` // "01:00.0", empty when unknown
}

func gpuVendor(pciID string) string {
	switch strings.ToLower(strings.SplitN(pciID, ":", 2)[0]) {
	case "10de":
		return "nvidia"
	case "1002":
		return "amd"
	case "8086":
		return "intel"
	}
	return "other"
}

// ListGPUs scans this machine for graphics adapters.
func (a *App) ListGPUs() []GPU { return detectGPUs() }

func detectGPUs() []GPU {
	if runtime.GOOS != "linux" {
		return nil // shortcut: switching is Linux-only; Windows needs a per-exe registry preference instead
	}
	if out, err := exec.Command("lspci", "-nn").Output(); err == nil {
		if gpus := parseLspci(string(out)); len(gpus) > 0 {
			return gpus
		}
	}
	return drmGPUs()
}

func findGPU(id string) GPU {
	for _, g := range detectGPUs() {
		if g.ID == id {
			return g
		}
	}
	return GPU{}
}

var lspciGPURe = regexp.MustCompile(`^([0-9a-f]{2}:[0-9a-f]{2}\.[0-9a-f])\s+.*?:\s+(.*)\s+\[([0-9a-f]{4}):([0-9a-f]{4})\]`)

func parseLspci(out string) []GPU {
	var gpus []GPU
	for _, line := range strings.Split(out, "\n") {
		lower := strings.ToLower(line)
		if !strings.Contains(lower, "vga compatible controller") &&
			!strings.Contains(lower, "3d controller") &&
			!strings.Contains(lower, "display controller") {
			continue
		}
		m := lspciGPURe.FindStringSubmatch(strings.TrimSpace(line))
		if m == nil {
			continue
		}
		name := strings.TrimSpace(m[2])
		for _, junk := range []string{"Advanced Micro Devices, ", "Corporation ", "Inc. ", "[AMD/ATI] ", "[AMD] "} {
			name = strings.Replace(name, junk, "", 1)
		}
		id := m[3] + ":" + m[4]
		gpus = append(gpus, GPU{ID: id, Name: name, Vendor: gpuVendor(id), Address: m[1]})
	}
	return gpus
}

// drmGPUs is the fallback when lspci is missing: ids only, no marketing names.
func drmGPUs() []GPU {
	cards, _ := filepath.Glob("/sys/class/drm/card[0-9]*/device/vendor")
	seen := map[string]bool{}
	var gpus []GPU
	for _, path := range cards {
		readID := func(f string) string {
			b, _ := os.ReadFile(filepath.Join(filepath.Dir(path), f))
			return strings.TrimPrefix(strings.TrimSpace(string(b)), "0x")
		}
		id := readID("vendor") + ":" + readID("device")
		if !strings.Contains(id, ":") || seen[id] {
			continue
		}
		seen[id] = true
		gpus = append(gpus, GPU{ID: id, Name: "GPU " + id, Vendor: gpuVendor(id)})
	}
	return gpus
}

func nvidiaICD() string {
	matches, _ := filepath.Glob("/usr/share/vulkan/icd.d/nvidia_icd*.json")
	for _, m := range matches {
		if !strings.Contains(m, ".i686.") {
			return m
		}
	}
	return ""
}

// gpuEnv returns env vars that pin a Java process to gpu. Zero GPU = system default.
func gpuEnv(gpu GPU, profile string) []string {
	if gpu.ID == "" || runtime.GOOS != "linux" {
		return nil
	}
	switch gpu.Vendor {
	case "nvidia":
		if profile == "vk" {
			if icd := nvidiaICD(); icd != "" {
				return []string{"VK_ICD_FILENAMES=" + icd}
			}
			return nil // no NVIDIA Vulkan driver: use default rather than crash
		}
		env := []string{"__NV_PRIME_RENDER_OFFLOAD=1", "__GLX_VENDOR_LIBRARY_NAME=nvidia"}
		if fileExists("/usr/share/glvnd/egl_vendor.d/10_nvidia.json") {
			env = append(env, "__EGL_VENDOR_LIBRARY_FILENAMES=/usr/share/glvnd/egl_vendor.d/10_nvidia.json")
		}
		return env
	}

	// Mesa (AMD/Intel): pick the adapter by PCI slot, or by id when the slot is unknown.
	if profile == "vk" {
		return []string{"MESA_VK_DEVICE_SELECT=" + gpu.ID}
	}
	dri := gpu.ID
	if gpu.Address != "" {
		dri = "pci-0000_" + strings.NewReplacer(":", "_", ".", "_").Replace(gpu.Address)
	}
	return []string{"DRI_PRIME=" + dri}
}
