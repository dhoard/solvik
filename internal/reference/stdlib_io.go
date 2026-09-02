package reference

// Network and process helpers, plus CLI globals, for the standard library.

import (
	"net/http"
	"os"
	"os/exec"
	"strings"
)

// programArgs is the CLI argument list exposed via process.args().
var programArgs = []string{}

func httpNewRequest(method, url string, body interface{ Read([]byte) (int, error) }) (*http.Request, error) {
	return http.NewRequest(method, url, body)
}

func httpClient() *http.Client { return &http.Client{Timeout: 30 * 1e9} } // 30s

func processRun(args []any) any {
	cmd := exec.Command(mapKeyString(args[0]), argStrings(args[1:])...)
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stderr
	err := cmd.Run()
	if err == nil {
		return int64(0)
	}
	if ee, ok := err.(*exec.ExitError); ok {
		return int64(ee.ExitCode())
	}
	return int64(-1)
}

func processCapture(args []any) *solvikMap {
	cmd := exec.Command(mapKeyString(args[0]), argStrings(args[1:])...)
	var stdout, stderr strings.Builder
	cmd.Stdout = &stdout
	cmd.Stderr = &stderr
	err := cmd.Run()
	code := int64(-1)
	if err == nil {
		code = 0
	} else if ee, ok := err.(*exec.ExitError); ok {
		code = int64(ee.ExitCode())
	}
	out := newSolvikMap()
	out.set("status", code)
	out.set("stdout", stdout.String())
	out.set("stderr", stderr.String())
	return out
}

func argStrings(args []any) []string {
	out := make([]string, len(args))
	for i, a := range args {
		out[i] = mapKeyString(a)
	}
	return out
}
