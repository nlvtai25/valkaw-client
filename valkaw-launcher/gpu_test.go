package main

import "testing"

const lspciSample = `00:02.0 VGA compatible controller [0300]: Intel Corporation Raptor Lake-P [Iris Xe Graphics] [8086:a7a0] (rev 04)
01:00.0 VGA compatible controller [0300]: NVIDIA Corporation GA107BM / GN20-P0-R-K2 [GeForce RTX 3050 6GB Laptop GPU] [10de:25ac] (rev a1)
74:00.0 VGA compatible controller [0300]: Advanced Micro Devices, Inc. [AMD/ATI] Rembrandt [Radeon 680M] [1002:1681] (rev c9)
03:00.0 Non-Volatile memory controller [0108]: Samsung Electronics Co Ltd NVMe SSD [144d:a80a]
`

func TestParseLspci(t *testing.T) {
	gpus := parseLspci(lspciSample)
	if len(gpus) != 3 {
		t.Fatalf("got %d gpus: %+v", len(gpus), gpus)
	}
	want := []GPU{
		{ID: "8086:a7a0", Vendor: "intel", Address: "00:02.0"},
		{ID: "10de:25ac", Vendor: "nvidia", Address: "01:00.0"},
		{ID: "1002:1681", Vendor: "amd", Address: "74:00.0"},
	}
	for i, w := range want {
		if gpus[i].ID != w.ID || gpus[i].Vendor != w.Vendor || gpus[i].Address != w.Address {
			t.Errorf("gpu %d = %+v, want %+v", i, gpus[i], w)
		}
	}
}

func TestGPUEnv(t *testing.T) {
	if env := gpuEnv(GPU{}, "gl"); env != nil {
		t.Errorf("system default should set no env, got %v", env)
	}
	nv := GPU{ID: "10de:25ac", Vendor: "nvidia", Address: "01:00.0"}
	if env := gpuEnv(nv, "gl"); len(env) == 0 || env[0] != "__NV_PRIME_RENDER_OFFLOAD=1" {
		t.Errorf("nvidia gl env = %v", env)
	}
	if env := gpuEnv(nv, "vk"); len(env) != 1 || len(env[0]) < len("VK_ICD_FILENAMES=") ||
		env[0][:len("VK_ICD_FILENAMES=")] != "VK_ICD_FILENAMES=" {
		t.Errorf("nvidia vk env = %v", env)
	}
	amd := GPU{ID: "1002:1681", Vendor: "amd", Address: "74:00.0"}
	if env := gpuEnv(amd, "gl"); len(env) != 1 || env[0] != "DRI_PRIME=pci-0000_74_00_0" {
		t.Errorf("amd gl env = %v", env)
	}
	if env := gpuEnv(amd, "vk"); len(env) != 1 || env[0] != "MESA_VK_DEVICE_SELECT=1002:1681" {
		t.Errorf("amd vk env = %v", env)
	}
}
