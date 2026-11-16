package core

import (
	"archive/zip"
	"fmt"
	"os"
	"testing"
)

func TestValidateOfflineJar(t *testing.T) {
	// craft a minimal valid jar: fabric.mod.json + a class entry
	jar := makeTestJar(t, map[string]string{
		"fabric.mod.json":           `{"schemaVersion":1,"id":"valkawclient","depends":{"minecraft":"1.21.11"}}`,
		"com/valkaw/client/V.class": "CAFEBABE",
	})
	if msg := validateOfflineJar(jar, "1.21.11", "gl"); msg != "" {
		t.Fatalf("valid jar rejected: %s", msg)
	}
	// wrong target version
	if msg := validateOfflineJar(jar, "26.1.2", "gl"); msg == "" {
		t.Fatal("wrong-version jar not rejected")
	}
	// tampered: executable inside
	bad := makeTestJar(t, map[string]string{
		"fabric.mod.json": `{"schemaVersion":1,"id":"valkawclient","depends":{"minecraft":"1.21.11"}}`,
		"evil.exe":        "MZ",
	})
	if msg := validateOfflineJar(bad, "1.21.11", "gl"); msg == "" {
		t.Fatal("executable inside jar not rejected")
	}
	// zip-slip path
	slip := makeTestJar(t, map[string]string{
		"fabric.mod.json": `{"schemaVersion":1,"id":"valkawclient","depends":{"minecraft":"1.21.11"}}`,
		"../../evil":      "x",
	})
	if msg := validateOfflineJar(slip, "1.21.11", "gl"); msg == "" {
		t.Fatal("zip-slip entry not rejected")
	}
}

func makeTestJar(t *testing.T, entries map[string]string) string {
	t.Helper()
	dir := t.TempDir()
	path := dir + "/test.jar"
	zw, err := os.Create(path)
	if err != nil {
		t.Fatal(err)
	}
	z := zip.NewWriter(zw)
	for name, content := range entries {
		w, err := z.Create(name)
		if err != nil {
			t.Fatal(err)
		}
		if len(content) < 4096 {
			for len(content) < 4096 {
				content += fmt.Sprintf("%04d", len(content))
			}
		}
		if _, err := w.Write([]byte(content)); err != nil {
			t.Fatal(err)
		}
	}
	if err := z.Close(); err != nil {
		t.Fatal(err)
	}
	zw.Close()
	return path
}
