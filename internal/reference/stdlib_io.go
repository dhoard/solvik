package reference

// Network helpers and CLI globals for the standard library.

import (
	"net/http"
)

// programArgs is the CLI argument list exposed via args().
var programArgs = []string{}

func httpNewRequest(method, url string, body interface{ Read([]byte) (int, error) }) (*http.Request, error) {
	return http.NewRequest(method, url, body)
}

func httpClient() *http.Client { return &http.Client{Timeout: 30 * 1e9} } // 30s
