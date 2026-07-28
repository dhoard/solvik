// Copyright (c) 2026-present Douglas Hoard
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

// Package fetcher handles downloading and caching remote module files.
// Cache uses atomic rename for concurrency safety across multiple processes.
package fetcher

import (
	"crypto/sha256"
	"crypto/tls"
	"encoding/hex"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"strings"
)

// CacheDir returns the cache directory (~/.solvik/cache).
func CacheDir() (string, error) {
	home, err := os.UserHomeDir()
	if err != nil {
		return "", fmt.Errorf("cannot determine home directory: %v", err)
	}
	return filepath.Join(home, ".solvik", "cache"), nil
}

// Fetch downloads a URL (http or https), optionally validates sha-256 checksum
// and caches the result using atomic rename for concurrency safety.
// If insecure is true, http:// URLs are allowed and TLS verification is skipped.
func Fetch(rawURL string, checksum string, insecure bool) (string, error) {
	if strings.HasPrefix(rawURL, "http://") && !insecure {
		return "", fmt.Errorf("http URLs require insecure flag: %s", rawURL)
	}
	if !strings.HasPrefix(rawURL, "https://") && !strings.HasPrefix(rawURL, "http://") {
		return "", fmt.Errorf("unsupported URL scheme: %s", rawURL)
	}
	if !insecure && checksum == "" {
		return "", fmt.Errorf("sha-256 checksum is required")
	}
	if checksum != "" && len(checksum) != 64 {
		return "", fmt.Errorf("sha-256 checksum must be 64 hex characters, got %d", len(checksum))
	}

	cacheDir, err := CacheDir()
	if err != nil {
		return "", err
	}

	// Cache key = SHA-256 of the URL
	urlHash := fmt.Sprintf("%x", sha256.Sum256([]byte(rawURL)))
	finalPath := filepath.Join(cacheDir, urlHash, "module.sol")

	// Fast path: check if cache entry exists and is valid
	if data, err := os.ReadFile(finalPath); err == nil {
		if err := verifySHA256(data, checksum); err == nil {
			return finalPath, nil
		}
		// Cache corrupted or content changed — re-download
		os.RemoveAll(filepath.Dir(finalPath))
	}

	// Ensure cache directory exists
	cacheEntryDir := filepath.Dir(finalPath)
	if err := os.MkdirAll(cacheEntryDir, 0755); err != nil {
		return "", fmt.Errorf("cannot create cache directory: %v", err)
	}

	// Download to a temp file in the same directory for atomic rename
	tmpFile, err := os.CreateTemp(cacheEntryDir, ".tmp_*")
	if err != nil {
		return "", fmt.Errorf("cannot create temp file: %v", err)
	}
	tmpPath := tmpFile.Name()
	tmpFile.Close() // close before rename (required on Windows)

	// Clean up temp file on any failure
	success := false
	defer func() {
		if !success {
			os.Remove(tmpPath)
		}
	}()

	// Create HTTP client with optional TLS verification skip
	// Use default client (respects http.DefaultClient) unless insecure requires cert skip
	var httpResp *http.Response
	var httpErr error
	if insecure && strings.HasPrefix(rawURL, "https://") {
		transport := &http.Transport{
			TLSClientConfig: &tls.Config{InsecureSkipVerify: true},
		}
		client := &http.Client{Transport: transport}
		httpResp, httpErr = client.Get(rawURL)
	} else {
		httpResp, httpErr = http.Get(rawURL)
	}
	if httpErr != nil {
		return "", fmt.Errorf("cannot fetch %s: %v", rawURL, httpErr)
	}
	defer httpResp.Body.Close()

	if httpResp.StatusCode != http.StatusOK {
		return "", fmt.Errorf("cannot fetch %s: HTTP %d", rawURL, httpResp.StatusCode)
	}

	data, err := io.ReadAll(httpResp.Body)
	if err != nil {
		return "", fmt.Errorf("cannot read response from %s: %v", rawURL, err)
	}

	// Validate checksum before caching
	if err := verifySHA256(data, checksum); err != nil {
		return "", fmt.Errorf("checksum mismatch for %s: %v", rawURL, err)
	}

	// Write validated content to temp file
	if err := os.WriteFile(tmpPath, data, 0644); err != nil {
		return "", fmt.Errorf("cannot write temp file: %v", err)
	}

	// Atomic rename: on Unix this is a single atomic filesystem operation.
	// On Windows (NTFS), os.Rename is atomic within the same volume.
	if err := os.Rename(tmpPath, finalPath); err != nil {
		return "", fmt.Errorf("cannot install cache entry: %v", err)
	}

	success = true
	return finalPath, nil
}

// VerifyFile reads a local file and validates its sha-256 checksum if provided.
// Returns the file content as a string.
func VerifyFile(path string, checksum string) (string, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return "", err
	}
	if checksum != "" {
		if err := verifySHA256(data, checksum); err != nil {
			return "", fmt.Errorf("checksum mismatch for %s: %v", path, err)
		}
	}
	return string(data), nil
}

// verifySHA256 checks that data matches the given sha-256 hex string.
// Hex must be 64 characters (32 bytes), case-insensitive.
func verifySHA256(data []byte, hexStr string) error {
	expected, err := hex.DecodeString(hexStr)
	if err != nil {
		return fmt.Errorf("invalid hex in checksum: %v", err)
	}
	if len(expected) != sha256.Size {
		return fmt.Errorf("expected %d bytes for sha-256, got %d", sha256.Size, len(expected))
	}
	actual := sha256.Sum256(data)
	for i := range actual {
		if actual[i] != expected[i] {
			return fmt.Errorf("sha-256 mismatch")
		}
	}
	return nil
}

// SHA256Hex returns the lowercase hex sha-256 of the given data.
func SHA256Hex(data []byte) string {
	h := sha256.Sum256(data)
	return fmt.Sprintf("%x", h)
}
