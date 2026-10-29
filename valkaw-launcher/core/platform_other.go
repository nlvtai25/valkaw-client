//go:build !windows

package core

import "os/exec"

// applyGpuPreference is a no-op outside Windows: Linux picks the GPU through
// DRI_PRIME or VK_ICD_FILENAMES at launch time.
func applyGpuPreference(string, int) error { return nil }

func hideWindow(*exec.Cmd) {}
