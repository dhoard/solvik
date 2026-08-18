package fetcher

import (
	"crypto/sha256"
	"fmt"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestSHA256Hex(t *testing.T) {
	data := []byte("hello world")
	h := SHA256Hex(data)
	if len(h) != 64 {
		t.Fatalf("expected 64 hex chars, got %d: %s", len(h), h)
	}
	expected := fmt.Sprintf("%x", sha256.Sum256(data))
	if h != expected {
		t.Fatalf("expected %s, got %s", expected, h)
	}
}

func TestVerifySHA256(t *testing.T) {
	data := []byte("test content")
	hexStr := SHA256Hex(data)

	if err := verifySHA256(data, hexStr); err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if err := verifySHA256(data, "0000000000000000000000000000000000000000000000000000000000000000"); err == nil {
		t.Fatal("expected mismatch error")
	}

	if err := verifySHA256(data, "abc"); err == nil {
		t.Fatal("expected error for short hex")
	}
}

func TestVerifyFile(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "test.sol")
	content := "package test\nfunc main() {}\n"
	os.WriteFile(path, []byte(content), 0644)

	checksum := SHA256Hex([]byte(content))

	// Valid checksum
	got, err := VerifyFile(path, checksum)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if got != content {
		t.Fatalf("expected %q, got %q", content, got)
	}

	// Invalid checksum
	_, err = VerifyFile(path, "0000000000000000000000000000000000000000000000000000000000000000")
	if err == nil {
		t.Fatal("expected mismatch error")
	}

	// No checksum
	got, err = VerifyFile(path, "")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if got != content {
		t.Fatalf("expected %q, got %q", content, got)
	}
}

func TestCacheDir(t *testing.T) {
	dir, err := CacheDir()
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if !strings.HasSuffix(dir, ".solvik/cache") {
		t.Fatalf("expected path ending in .solvik/cache, got %s", dir)
	}
}

func TestFetchHTTPS(t *testing.T) {
	// Create a test HTTPS server
	content := "package test\nfunc greet() -> string { return \"hi\" }\n"
	checksum := SHA256Hex([]byte(content))

	server := httptest.NewTLSServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Write([]byte(content))
	}))
	defer server.Close()

	// Use the test server's URL (it starts with https://)
	url := server.URL

	// Create HTTP client that trusts the test server's self-signed cert
	client := server.Client()
	savedClient := http.DefaultClient
	http.DefaultClient = client
	defer func() { http.DefaultClient = savedClient }()

	// Fetch should succeed
	path, err := Fetch(url, checksum, false)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	// Verify cached content
	data, err := os.ReadFile(path)
	if err != nil {
		t.Fatalf("cannot read cached file: %v", err)
	}
	if string(data) != content {
		t.Fatalf("expected %q, got %q", content, string(data))
	}

	// Second fetch should use cache (no network)
	path2, err := Fetch(url, checksum, false)
	if err != nil {
		t.Fatalf("unexpected error on second fetch: %v", err)
	}
	if path2 != path {
		t.Fatalf("expected same cache path %s, got %s", path, path2)
	}
}

func TestFetchChecksumMismatch(t *testing.T) {
	content := "package test\n"
	badChecksum := "0000000000000000000000000000000000000000000000000000000000000000"

	server := httptest.NewTLSServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Write([]byte(content))
	}))
	defer server.Close()

	client := server.Client()
	savedClient := http.DefaultClient
	http.DefaultClient = client
	defer func() { http.DefaultClient = savedClient }()

	_, err := Fetch(server.URL, badChecksum, false)
	if err == nil {
		t.Fatal("expected checksum mismatch error")
	}
	if !strings.Contains(err.Error(), "checksum mismatch") {
		t.Fatalf("expected checksum mismatch error, got: %v", err)
	}
}

func TestFetchHTTPRejected(t *testing.T) {
	// Plain HTTP server (not TLS)
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Write([]byte("content"))
	}))
	defer server.Close()

	_, err := Fetch(server.URL, "0000000000000000000000000000000000000000000000000000000000000000", false)
	if err == nil {
		t.Fatal("expected error for http URL")
	}
	if !strings.Contains(err.Error(), "insecure flag") {
		t.Fatalf("expected 'insecure flag' error, got: %v", err)
	}
}

func TestFetchNoChecksum(t *testing.T) {
	_, err := Fetch("https://example.com/test", "", false)
	if err == nil {
		t.Fatal("expected error for missing checksum")
	}
	if !strings.Contains(err.Error(), "checksum is required") {
		t.Fatalf("expected 'checksum is required' error, got: %v", err)
	}
}

func TestAtomicRename(t *testing.T) {
	// Verify that after a successful fetch, the temp file is gone
	// and only module.sol exists
	content := "package test\n"
	checksum := SHA256Hex([]byte(content))

	server := httptest.NewTLSServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Write([]byte(content))
	}))
	defer server.Close()

	client := server.Client()
	savedClient := http.DefaultClient
	http.DefaultClient = client
	defer func() { http.DefaultClient = savedClient }()

	path, err := Fetch(server.URL, checksum, false)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	// Check no .tmp files exist in the cache directory
	cacheDir := filepath.Dir(path)
	entries, err := os.ReadDir(cacheDir)
	if err != nil {
		t.Fatalf("cannot read cache dir: %v", err)
	}
	for _, e := range entries {
		if strings.HasPrefix(e.Name(), ".tmp_") {
			t.Fatalf("temp file left behind: %s", e.Name())
		}
	}
}
