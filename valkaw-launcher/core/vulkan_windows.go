//go:build windows

package core

import (
	"encoding/binary"
	"syscall"
	"unsafe"
)

// Vulkan structures are built in byte buffers with explicit offsets so the
// layout matches the C ABI without depending on Go struct padding.
var (
	vulkanDLL                       = syscall.NewLazyDLL("vulkan-1.dll")
	procEnumerateInstanceVersion    = vulkanDLL.NewProc("vkEnumerateInstanceVersion")
	procCreateInstance              = vulkanDLL.NewProc("vkCreateInstance")
	procDestroyInstance             = vulkanDLL.NewProc("vkDestroyInstance")
	procEnumeratePhysicalDevices    = vulkanDLL.NewProc("vkEnumeratePhysicalDevices")
	procGetPhysicalDeviceProperties = vulkanDLL.NewProc("vkGetPhysicalDeviceProperties")
)

const (
	vkSuccess                     = 0
	vkStructureTypeApplication    = 0
	vkStructureTypeInstanceCreate = 1
	vkAPIVersion10                = 1 << 22
)

// probeVulkan loads vulkan-1.dll and asks the loader for every physical GPU.
func probeVulkan() vulkanProbe {
	if err := vulkanDLL.Load(); err != nil {
		return vulkanProbe{Err: "vulkan-1.dll not found"}
	}
	p := vulkanProbe{Loader: true, Verified: true}

	// vkEnumerateInstanceVersion exists from Vulkan 1.1; 1.0 loaders do not export it.
	p.LoaderVersion = vkAPIVersion10
	if err := procEnumerateInstanceVersion.Find(); err == nil {
		var v uint32
		if ret, _, _ := procEnumerateInstanceVersion.Call(uintptr(unsafe.Pointer(&v))); int32(ret) == vkSuccess && v != 0 {
			p.LoaderVersion = v
		}
	}

	// VkApplicationInfo (48 bytes) + VkInstanceCreateInfo (64 bytes).
	app := make([]byte, 48)
	putU32(app, 0, vkStructureTypeApplication)
	putU32(app, 24, 1)              // applicationVersion
	putU32(app, 44, vkAPIVersion10) // apiVersion

	create := make([]byte, 64)
	putU32(create, 0, vkStructureTypeInstanceCreate)
	putU64(create, 24, uint64(uintptr(unsafe.Pointer(&app[0]))))

	var instance uintptr
	if ret, _, _ := procCreateInstance.Call(uintptr(unsafe.Pointer(&create[0])), 0, uintptr(unsafe.Pointer(&instance))); int32(ret) != vkSuccess {
		p.Err = "Vulkan loader cannot create an instance"
		return p
	}
	defer procDestroyInstance.Call(instance, 0)

	var count uint32
	if ret, _, _ := procEnumeratePhysicalDevices.Call(instance, uintptr(unsafe.Pointer(&count)), 0); int32(ret) != vkSuccess || count == 0 {
		p.Err = "Vulkan loader reports no GPU"
		return p
	}
	handles := make([]uintptr, count)
	if ret, _, _ := procEnumeratePhysicalDevices.Call(instance, uintptr(unsafe.Pointer(&count)), uintptr(unsafe.Pointer(&handles[0]))); int32(ret) != vkSuccess {
		p.Err = "Vulkan device enumeration failed"
		return p
	}

	// VkPhysicalDeviceProperties starts with apiVersion, driverVersion, vendorID,
	// deviceID, deviceType and deviceName[256]; the trailing structs are unused.
	props := make([]byte, 2048)
	for _, h := range handles {
		for i := range props {
			props[i] = 0
		}
		procGetPhysicalDeviceProperties.Call(h, uintptr(unsafe.Pointer(&props[0])))
		p.Devices = append(p.Devices, vulkanDevice{
			Name:          cString(props[20:276]),
			APIVersion:    binary.LittleEndian.Uint32(props[0:4]),
			DriverVersion: binary.LittleEndian.Uint32(props[4:8]),
			VendorID:      binary.LittleEndian.Uint32(props[8:12]),
			DeviceID:      binary.LittleEndian.Uint32(props[12:16]),
			Type:          binary.LittleEndian.Uint32(props[16:20]),
		})
	}
	return p
}

func putU32(b []byte, off int, v uint32) { binary.LittleEndian.PutUint32(b[off:], v) }

func putU64(b []byte, off int, v uint64) { binary.LittleEndian.PutUint64(b[off:], v) }

func cString(b []byte) string {
	for i, c := range b {
		if c == 0 {
			return string(b[:i])
		}
	}
	return string(b)
}
