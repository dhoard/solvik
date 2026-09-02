package bootstrap

use file:token

// A compact, source-level lexer. It intentionally models the lexical surface
// needed by the bootstrap parser: identifiers/keywords, integer and float
// spellings, strings/chars, comments, newlines, and multi-character operators.

func isDigit(c: char) -> bool {
    return c >= '0' && c <= '9'
}

func isLetter(c: char) -> bool {
    return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_'
}

func isIdentPart(c: char) -> bool {
    return isLetter(c) || isDigit(c)
}

pub func byteAt(source: string, index: int) -> byte {
    return byte(int(source.charAt(index)))
}

func appendToken(tokens: map<int, Token>, index: int, item: Token) -> map<int, Token> {
    mut result: map<int, Token> = tokens
    result[index] = item
    return result
}

pub func lex(source: string) -> TokenStream {
    mut tokens: map<int, Token> = {}
    mut count: int = 0
    mut i: int = 0
    mut line: int = 1
    mut column: int = 1
    length: int = source.len()
    backslash: char = "\\".charAt(0)
    quote: char = "\"".charAt(0)
    newline: char = "\n".charAt(0)

    while i < length {
        c: char = source.charAt(i)

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

        startLine: int = line
        startColumn: int = column

        if isLetter(c) {
            mut text: string = ""
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
            mut text: string = ""
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
            mut text: string = ""
            i = i + 1
            column = column + 1
            mut closed: bool = false
            while i < length {
                current: char = source.charAt(i)
                if current == quote {
                    closed = true
                    i = i + 1
                    column = column + 1
                    break
                }
                if current == backslash && i + 1 < length {
                    escaped: char = source.charAt(i + 1)
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
            mut text: string = ""
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

        mut two: string = ""
        if i + 1 < length {
            two = source.substring(i, i + 2)
        }
        mut three: string = ""
        if i + 2 < length {
            three = source.substring(i, i + 3)
        }

        mut symbol: string = string(c)
        mut width: int = 1
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
