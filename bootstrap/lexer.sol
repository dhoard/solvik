package bootstrap

use file:token

// A compact, source-level lexer. It intentionally models the lexical surface
// needed by the bootstrap parser: identifiers/keywords, integer and float
// spellings, strings/chars, comments, newlines, and multi-character operators.

func isDigit(c: Char) -> Bool {
    return c >= '0' && c <= '9'
}

func isLetter(c: Char) -> Bool {
    return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_'
}

func isIdentPart(c: Char) -> Bool {
    return isLetter(c) || isDigit(c)
}

pub func byteAt(source: String, index: Int) -> Byte {
    return byte(int(source.charAt(index)))
}

func appendToken(tokens: Map<Int, Token>, index: Int, item: Token) -> Map<Int, Token> {
    mut result: Map<Int, Token> = tokens
    result[index] = item
    return result
}

pub func lex(source: String) -> TokenStream {
    mut tokens: Map<Int, Token> = {}
    mut count: Int = 0
    mut i: Int = 0
    mut line: Int = 1
    mut column: Int = 1
    length: Int = source.len()
    backslash: Char = "\\".charAt(0)
    quote: Char = "\"".charAt(0)
    newline: Char = "\n".charAt(0)

    while i < length {
        c: Char = source.charAt(i)

        if c == ' ' || c == '\t' || c == '\r' {
            i = i + 1
            column = column + 1
            continue
        }

        if c == newline {
            tokens[count] = token("newline", "\\n", line, column)
            count = count + 1
            i = i + 1
            line = line + 1
            column = 1
            continue
        }

        if c == '/' && i + 1 < length && source.charAt(i + 1) == '/' {
            i = i + 2
            column = column + 2
            while i < length && source.charAt(i) != newline {
                i = i + 1
                column = column + 1
            }
            continue
        }

        startLine: Int = line
        startColumn: Int = column

        if isLetter(c) {
            mut text: String = ""
            while i < length && isIdentPart(source.charAt(i)) {
                text = text .. string(source.charAt(i))
                i = i + 1
                column = column + 1
            }
            tokens[count] = token("identifier", text, startLine, startColumn)
            count = count + 1
            continue
        }

        if isDigit(c) {
            mut text: String = ""
            while i < length && (isIdentPart(source.charAt(i)) || source.charAt(i) == '.') {
                text = text .. string(source.charAt(i))
                i = i + 1
                column = column + 1
            }
            tokens[count] = token("number", text, startLine, startColumn)
            count = count + 1
            continue
        }

        if c == quote {
            mut text: String = ""
            i = i + 1
            column = column + 1
            mut closed: Bool = false
            while i < length {
                current: Char = source.charAt(i)
                if current == quote {
                    closed = true
                    i = i + 1
                    column = column + 1
                    break
                }
                if current == backslash && i + 1 < length {
                    escaped: Char = source.charAt(i + 1)
                    if escaped == 'n' {
                        text = text .. "\n"
                    } else if escaped == 't' {
                        text = text .. "\t"
                    } else if escaped == 'r' {
                        text = text .. "\r"
                    } else {
                        text = text .. string(escaped)
                    }
                    i = i + 2
                    column = column + 2
                    continue
                }
                text = text .. string(current)
                i = i + 1
                column = column + 1
            }
            if closed {
                tokens[count] = token("string", text, startLine, startColumn)
            } else {
                tokens[count] = token("error", "unterminated string", startLine, startColumn)
            }
            count = count + 1
            continue
        }

        if c == '\'' {
            mut text: String = ""
            i = i + 1
            column = column + 1
            if i < length && source.charAt(i) == backslash && i + 1 < length {
                text = string(source.charAt(i + 1))
                i = i + 2
                column = column + 2
            } else if i < length {
                text = string(source.charAt(i))
                i = i + 1
                column = column + 1
            }
            if i < length && source.charAt(i) == '\'' {
                i = i + 1
                column = column + 1
                tokens[count] = token("character", text, startLine, startColumn)
            } else {
                tokens[count] = token("error", "unterminated character", startLine, startColumn)
            }
            count = count + 1
            continue
        }

        mut two: String = ""
        if i + 1 < length {
            two = source.substring(i, i + 2)
        }
        mut three: String = ""
        if i + 2 < length {
            three = source.substring(i, i + 3)
        }

        mut symbol: String = string(c)
        mut width: Int = 1
        if three == "..." {
            symbol = three
            width = 3
        } else if two == "->" || two == "==" || two == "!=" || two == "<=" || two == ">=" || two == "&&" || two == "||" || two == ".." || two == "??" {
            symbol = two
            width = 2
        }
        tokens[count] = token("symbol", symbol, startLine, startColumn)
        count = count + 1
        i = i + width
        column = column + width
    }

    tokens[count] = token("end", "", line, column)
    return TokenStream { tokens: tokens, count: count + 1 }
}
