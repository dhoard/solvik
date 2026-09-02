package reference

import "strconv"

func isAlpha(c byte) bool {
	return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c >= 0x80
}

func isDigit(c byte) bool { return c >= '0' && c <= '9' }

func isAlphaNum(c byte) bool { return isAlpha(c) || isDigit(c) }

func lowerByte(c byte) byte {
	if c >= 'A' && c <= 'Z' {
		return c + 32
	}
	return c
}

func toLower(s string) string {
	b := []byte(s)
	for i := range b {
		b[i] = lowerByte(b[i])
	}
	return string(b)
}

func stringsContainsByte(s string, c byte) bool {
	for i := 0; i < len(s); i++ {
		if s[i] == c {
			return true
		}
	}
	return false
}

func hasPrefixStr(s, prefix string) bool {
	return len(s) >= len(prefix) && s[:len(prefix)] == prefix
}

func replaceAll(s, old, new string) string {
	out := ""
	for {
		idx := indexOf(s, old)
		if idx < 0 || old == "" {
			return out + s
		}
		out += s[:idx] + new
		s = s[idx+len(old):]
	}
}

func indexOf(s, sub string) int {
	for i := 0; i+len(sub) <= len(s); i++ {
		if s[i:i+len(sub)] == sub {
			return i
		}
	}
	return -1
}

func repeatStr(s string, n int) string {
	out := ""
	for i := 0; i < n; i++ {
		out += s
	}
	return out
}

func isAllHex(s string) bool {
	if s == "" {
		return false
	}
	for i := 0; i < len(s); i++ {
		c := s[i]
		if !(isDigit(c) || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')) {
			return false
		}
	}
	return true
}

func parseIntBase(s string, base int) int64 {
	v, err := strconv.ParseInt(s, base, 64)
	if err != nil {
		panic(parseErr(SourcePos{}, "invalid number %s", s))
	}
	return v
}

func absInt(v int64) int64 {
	if v < 0 {
		return -v
	}
	return v
}

func minInt(a, b int) int {
	if a < b {
		return a
	}
	return b
}
