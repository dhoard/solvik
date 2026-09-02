package reference

// NewLexerForTest exposes the lexer for debugging.
func NewLexerForTest(src, filename string) []token {
	l := newLexer(src, filename)
	return l.tokens()
}
