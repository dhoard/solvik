package parser

import "testing"

const testChecksum = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

func TestUseURLUnquotedValues(t *testing.T) {
	prog := requireParseSuccess(t, `package test
use url:https://example.com/lib.sol checksum:sha256:`+testChecksum+` insecure:true
func main() -> int {
    return 0
}
`)

	if len(prog.Uses) != 1 {
		t.Fatalf("expected one use declaration, got %d", len(prog.Uses))
	}
	use := prog.Uses[0]
	if use.SourceType != "url" || use.Path != "https://example.com/lib.sol" {
		t.Fatalf("unexpected use URL: %#v", use)
	}
	if use.Checksum != testChecksum || !use.Insecure {
		t.Fatalf("unexpected use options: %#v", use)
	}
}

func TestUseURLQuotedAndRawValues(t *testing.T) {
	prog := requireParseSuccess(t, `package test
use url:r"https://example.com/lib with spaces.sol" checksum:"sha256:`+testChecksum+`" insecure:false
func main() -> int {
    return 0
}
`)

	use := prog.Uses[0]
	if use.Path != "https://example.com/lib with spaces.sol" {
		t.Fatalf("raw URL was not preserved: %q", use.Path)
	}
	if use.Checksum != testChecksum || use.Insecure {
		t.Fatalf("unexpected quoted checksum/options: %#v", use)
	}
}

func TestUseChecksumPayloadMayBeQuoted(t *testing.T) {
	prog := requireParseSuccess(t, `package test
use url:https://example.com/lib.sol checksum:sha256:"`+testChecksum+`"
func main() -> int {
    return 0
}
`)

	if prog.Uses[0].Checksum != testChecksum {
		t.Fatalf("unexpected checksum: %q", prog.Uses[0].Checksum)
	}
}

func TestUseRejectsInvalidFlagsAndChecksums(t *testing.T) {
	tests := []struct {
		name string
		src  string
	}{
		{
			name: "missing algorithm",
			src:  `use url:https://example.com/lib.sol checksum:` + testChecksum,
		},
		{
			name: "short checksum",
			src:  `use url:https://example.com/lib.sol checksum:sha256:abcd`,
		},
		{
			name: "non hexadecimal checksum",
			src:  `use url:https://example.com/lib.sol checksum:sha256:zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz`,
		},
		{
			name: "quoted insecure value",
			src:  `use url:http://example.com/lib.sol insecure:"true"`,
		},
		{
			name: "duplicate checksum",
			src:  `use url:https://example.com/lib.sol checksum:sha256:` + testChecksum + ` checksum:sha256:` + testChecksum,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			requireParseError(t, "package test\n"+tt.src+"\nfunc main() -> int { return 0 }\n", "P048")
		})
	}
}
