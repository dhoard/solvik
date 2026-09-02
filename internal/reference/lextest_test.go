package reference

import (
	"fmt"
	"os"
	"testing"
)

func TestLexDebug(t *testing.T) {
	src, _ := os.ReadFile("/tmp/g9.sol")
	l := newLexer(string(src), "g9.sol")
	for _, tok := range l.tokens() {
		fmt.Printf("%d:%d kind=%d text=%q\n", tok.Pos.Line, tok.Pos.Col, tok.Kind, tok.Text)
	}
}
