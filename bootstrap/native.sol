package bootstrap_native
// Self-contained Phase 11 bootstrap compilation unit. The split files under
// bootstrap/ document the frontend layers; this unit is the executable target
// used by all three interpreters while cross-file native package typing evolves.


// Token model used by the first Solvik-native frontend bootstrap.
// The frontend keeps source positions explicit so diagnostics can be emitted
// without depending on an implementation-language parser.

pub enum TokenKind {
    Identifier
    Number
    String
    Character
    Symbol
    Newline
    End
    Error
}

pub struct Token {
    pub kind: string,
    pub text: string,
    pub line: int,
    pub column: int,
}

pub struct TokenStream {
    pub tokens: map<int, Token>,
    pub count: int,
}

pub func token(kind: string, text: string, line: int, column: int) -> Token {
    return Token { kind: kind, text: text, line: line, column: column }
}


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
    rightParen: string = ")"
    ellipsis: string = "..."

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

        // Store the common delimiters directly. The explicit literal also
        // keeps the value independent from the mutable cursor-local symbol
        // variable on all bootstrap interpreters.
        if c == '{' {
            tokens[count] = token("symbol", "{", startLine, startColumn)
            count = count + 1
            i = i + 1
            column = column + 1
            continue
        }
        if c == '}' {
            tokens[count] = token("symbol", "}", startLine, startColumn)
            count = count + 1
            i = i + 1
            column = column + 1
            continue
        }
        if c == '(' {
            tokens[count] = token("symbol", "(", startLine, startColumn)
            count = count + 1
            i = i + 1
            column = column + 1
            continue
        }
        if c == ')' {
            tokens[count] = token("symbol", rightParen, startLine, startColumn)
            count = count + 1
            i = i + 1
            column = column + 1
            continue
        }
        if c == '[' {
            tokens[count] = token("symbol", "[", startLine, startColumn)
            count = count + 1
            i = i + 1
            column = column + 1
            continue
        }
        if c == ']' {
            tokens[count] = token("symbol", "]", startLine, startColumn)
            count = count + 1
            i = i + 1
            column = column + 1
            continue
        }
        if c == ':' {
            tokens[count] = token("symbol", ":", startLine, startColumn)
            count = count + 1
            i = i + 1
            column = column + 1
            continue
        }
        if c == ',' {
            tokens[count] = token("symbol", ",", startLine, startColumn)
            count = count + 1
            i = i + 1
            column = column + 1
            continue
        }
        if c == ';' {
            tokens[count] = token("symbol", ";", startLine, startColumn)
            count = count + 1
            i = i + 1
            column = column + 1
            continue
        }

        if c == '.' && i + 2 < length && source.charAt(i + 1) == '.' && source.charAt(i + 2) == '.' {
            tokens[count] = token("symbol", ellipsis, startLine, startColumn)
            count = count + 1
            i = i + 3
            column = column + 3
            continue
        }
        if c == '-' && i + 1 < length && source.charAt(i + 1) == '>' {
            tokens[count] = token("symbol", "->", startLine, startColumn)
            count = count + 1
            i = i + 2
            column = column + 2
            continue
        }
        if c == '=' && i + 1 < length && source.charAt(i + 1) == '=' {
            tokens[count] = token("symbol", "==", startLine, startColumn)
            count = count + 1
            i = i + 2
            column = column + 2
            continue
        }
        if c == '!' && i + 1 < length && source.charAt(i + 1) == '=' {
            tokens[count] = token("symbol", "!=", startLine, startColumn)
            count = count + 1
            i = i + 2
            column = column + 2
            continue
        }
        if c == '<' && i + 1 < length && source.charAt(i + 1) == '=' {
            tokens[count] = token("symbol", "<=", startLine, startColumn)
            count = count + 1
            i = i + 2
            column = column + 2
            continue
        }
        if c == '>' && i + 1 < length && source.charAt(i + 1) == '=' {
            tokens[count] = token("symbol", ">=", startLine, startColumn)
            count = count + 1
            i = i + 2
            column = column + 2
            continue
        }
        if c == '&' && i + 1 < length && source.charAt(i + 1) == '&' {
            tokens[count] = token("symbol", "&&", startLine, startColumn)
            count = count + 1
            i = i + 2
            column = column + 2
            continue
        }
        if c == '|' && i + 1 < length && source.charAt(i + 1) == '|' {
            tokens[count] = token("symbol", "||", startLine, startColumn)
            count = count + 1
            i = i + 2
            column = column + 2
            continue
        }
        if c == '.' && i + 1 < length && source.charAt(i + 1) == '.' {
            tokens[count] = token("symbol", "..", startLine, startColumn)
            count = count + 1
            i = i + 2
            column = column + 2
            continue
        }
        if c == '?' && i + 1 < length && source.charAt(i + 1) == '?' {
            tokens[count] = token("symbol", "??", startLine, startColumn)
            count = count + 1
            i = i + 2
            column = column + 2
            continue
        }
        if c == '<' { tokens[count] = token("symbol", "<", startLine, startColumn)
        } else if c == '>' { tokens[count] = token("symbol", ">", startLine, startColumn)
        } else if c == '=' { tokens[count] = token("symbol", "=", startLine, startColumn)
        } else if c == '!' { tokens[count] = token("symbol", "!", startLine, startColumn)
        } else if c == '+' { tokens[count] = token("symbol", "+", startLine, startColumn)
        } else if c == '-' { tokens[count] = token("symbol", "-", startLine, startColumn)
        } else if c == '*' { tokens[count] = token("symbol", "*", startLine, startColumn)
        } else if c == '/' { tokens[count] = token("symbol", "/", startLine, startColumn)
        } else if c == '%' { tokens[count] = token("symbol", "%", startLine, startColumn)
        } else if c == '?' { tokens[count] = token("symbol", "?", startLine, startColumn)
        } else { tokens[count] = token("symbol", ".", startLine, startColumn)
        }
        count = count + 1
        i = i + 1
        column = column + 1
    }

    tokens[count] = token("end", "", line, column)
    return TokenStream { tokens: tokens, count: count + 1 }
}

// The bootstrap AST uses integer links instead of implementation-language
// pointers. This makes recursive trees straightforward to represent with
// Solvik's own map and struct values while keeping nodes inspectable.

pub enum AstKind {
    Program
    Package
    Function
    Parameter
    Block
    Variable
    Return
    If
    While
    Expression
    Name
    IntLiteral
    StringLiteral
    CharLiteral
    BooleanLiteral
    Unary
    Binary
    Call
    Closure
    Member
    Index
    Type
    Struct
    Field
    Enum
    EnumCase
    Trait
    Error
}

pub struct AstNode {
    pub kind: string,
    pub text: string,
    pub number: int,
    pub left: int,
    pub right: int,
    pub mut next: int,
    pub line: int,
    pub column: int,
}

pub struct Ast {
    pub nodes: map<int, AstNode>,
    pub count: int,
    pub root: int,
    pub errors: int,
}

pub func makeNode(kind: string, text: string, number: int, left: int, right: int, next: int, line: int, column: int) -> AstNode {
    return AstNode {
        kind: kind,
        text: text,
        number: number,
        left: left,
        right: right,
        next: next,
        line: line,
        column: column,
    }
}

pub func emptyNode() -> AstNode {
    return makeNode("error", "", 0, -1, -1, -1, 0, 0)
}

pub func emptyAst() -> Ast {
    return Ast { nodes: {}, count: 0, root: 0, errors: 0 }
}

pub func kindName(kind: string) -> string {
    return kind
}

pub func countChildren(tree: Ast, node: AstNode) -> int {
    mut total: int = 0
    if node.kind == "if" {
        total = total + countChain(tree, node.left)
        total = total + countChain(tree, node.right)
        total = total + countChain(tree, node.number)
    } else if node.kind == "function" || node.kind == "closure" {
        total = total + countChain(tree, node.left)
        total = total + countChain(tree, node.right)
    } else if node.kind == "block" || node.kind == "program" || node.kind == "struct" || node.kind == "enum" || node.kind == "trait" {
        total = total + countChain(tree, node.left)
        total = total + countChain(tree, node.right)
    } else if node.kind == "variable" || node.kind == "binary" || node.kind == "index" {
        total = total + countChain(tree, node.left)
        total = total + countChain(tree, node.right)
    } else if node.kind == "return" || node.kind == "unary" || node.kind == "member" || node.kind == "field" || node.kind == "enum_case" || node.kind == "expression" {
        total = total + countChain(tree, node.left)
    } else if node.kind == "call" {
        total = total + countChain(tree, node.left)
        total = total + countChain(tree, node.right)
    } else if node.kind == "while" {
        total = total + countChain(tree, node.left)
        total = total + countChain(tree, node.right)
    }
    return total
}

pub func countChain(tree: Ast, id: int) -> int {
    mut total: int = 0
    mut current: int = id
    while current >= 0 {
        node: AstNode = tree.nodes[current]
        total = total + 1 + countChildren(tree, node)
        current = node.next
    }
    return total
}

pub func countTree(tree: Ast) -> int {
    return countChain(tree, tree.root)
}


// A recursive-descent/Pratt parser whose mutable cursor and node arena are
// implemented with Solvik struct fields and mutating methods.

pub struct Link {
    pub mut head: int,
    pub mut tail: int,
}

pub struct Parser {
    pub tokens: TokenStream,
    pub tokenCount: int,
    // Maps are shared by value across nested mutating method calls, so the
    // cursor remains live even when a parser helper returns a node id.
    pub cursor: list<int>,
    pub nodeStore: list<AstNode>,
    pub mut nodeCount: int,
    pub mut errors: int,
    pub closeParen: string,

    pub func current() -> Token {
        return self.tokens.tokens[self.cursor[0]]
    }

    pub func at(text: string) -> bool {
        return current().text == text
    }

    pub func atEnd() -> bool {
        return current().kind == "end"
    }

    pub mut func advance() -> Token {
        token: Token = current()
        if atEnd() == false {
            self.cursor[0] = self.cursor[0] + 1
        }
        return token
    }

    pub mut func skipTerms() {
        while at("\\n") || at(";") || at(",") {
            if atEnd() == false {
                self.cursor[0] = self.cursor[0] + 1
            } else {
                break
            }
        }
    }

    pub mut func expect(text: string) -> Token {
        token: Token = current()
        if at(text) == false {
            self.errors = self.errors + 1
            if atEnd() == false {
                self.cursor[0] = self.cursor[0] + 1
            }
            return token
        }
        if atEnd() == false {
            self.cursor[0] = self.cursor[0] + 1
        }
        return token
    }

    pub mut func add(item: AstNode) -> int {
        id: int = self.nodeCount
        println("ADD " .. string(id))
        self.nodeStore[id] = item
        self.nodeCount = self.nodeCount + 1
        return id
    }

    pub mut func append(head: int, tail: int, item: int) -> Link {
        mut result: Link = Link { head: head, tail: tail }
        if head < 0 {
            result.head = item
        } else {
            previous: AstNode = self.nodeStore[tail]
            previous.next = item
            self.nodeStore[tail] = previous
        }
        result.tail = item
        return result
    }

    pub mut func parseType() -> int {
        first: Token = advance()
        mut text: string = first.text
        mut depth: int = 0
        if at(".") {
            while at(".") {
                text = text .. advance().text
                text = text .. advance().text
            }
        }
        if at("<") {
            depth = 1
            text = text .. advance().text
            while self.cursor[0] < self.tokenCount - 1 && depth > 0 {
                next: Token = advance()
                text = text .. next.text
                if next.text == "<" {
                    depth = depth + 1
                } else if next.text == ">" {
                    depth = depth - 1
                }
            }
        }
        if at("?") {
            text = text .. advance().text
        }
        return add(makeNode("type", text, 0, -1, -1, -1, first.line, first.column))
    }

    pub mut func parseParameter() -> int {
        name: Token = advance()
        expect(":")
        typeID: int = parseType()
        return add(makeNode("parameter", name.text, 0, typeID, -1, -1, name.line, name.column))
    }

    pub mut func parseFunction() -> int {
        start: Token = expect("func")
        name: Token = advance()
        mut parameters: Link = Link { head: -1, tail: -1 }
        expect("(")
        while self.cursor[0] < self.tokenCount - 1 && self.tokens.tokens[self.cursor[0]].text != closeParen {
            if current().text == "," || current().text == "\\n" {
                advance()
                continue
            }
            parameter: int = parseParameter()
            parameters = append(parameters.head, parameters.tail, parameter)
            if current().text == "," {
                advance()
            }
        }
        expect(closeParen)
        mut returnType: int = -1
        if at("->") {
            advance()
            returnType = parseType()
        }
        skipTerms()
        mut body: int = -1
        if at("{") {
            body = parseBlock()
        }
        return add(makeNode("function", name.text, returnType, parameters.head, body, -1, start.line, start.column))
    }

    pub mut func skipGenericParameters() {
        mut depth: int = 0
        if at("<") {
            depth = 1
            advance()
            while self.cursor[0] < self.tokenCount - 1 && depth > 0 {
                token: Token = advance()
                if token.text == "<" {
                    depth = depth + 1
                } else if token.text == ">" {
                    depth = depth - 1
                }
            }
        }
    }

    pub mut func parseStruct() -> int {
        start: Token = expect("struct")
        name: Token = advance()
        skipGenericParameters()
        expect("{")
        mut fields: Link = Link { head: -1, tail: -1 }
        mut methods: Link = Link { head: -1, tail: -1 }
        skipTerms()
        while self.cursor[0] < self.tokenCount - 1 {
            if at("}") {
                break
            }
            if at("pub") || at("mut") {
                advance()
            }
            if at("func") {
                method: int = parseFunction()
                methods = append(methods.head, methods.tail, method)
            } else {
                field: Token = advance()
                expect(":")
                typeID: int = parseType()
                fieldID: int = add(makeNode("field", field.text, 0, typeID, -1, -1, field.line, field.column))
                fields = append(fields.head, fields.tail, fieldID)
            }
            skipTerms()
        }
        expect("}")
        return add(makeNode("struct", name.text, 0, fields.head, methods.head, -1, start.line, start.column))
    }

    pub mut func parseEnum() -> int {
        start: Token = expect("enum")
        name: Token = advance()
        skipGenericParameters()
        expect("{")
        mut cases: Link = Link { head: -1, tail: -1 }
        skipTerms()
        while self.cursor[0] < self.tokenCount - 1 {
            if at("}") {
                break
            }
            caseToken: Token = advance()
            mut payload: Link = Link { head: -1, tail: -1 }
            if at("(") {
                advance()
                while self.cursor[0] < self.tokenCount - 1 {
                    if current().text == closeParen {
                        break
                    }
                    payloadType: int = parseType()
                    payload = append(payload.head, payload.tail, payloadType)
                    if at(",") { advance() }
                }
                expect(closeParen)
            }
            caseID: int = add(makeNode("enum_case", caseToken.text, 0, payload.head, -1, -1, caseToken.line, caseToken.column))
            cases = append(cases.head, cases.tail, caseID)
            skipTerms()
        }
        expect("}")
        return add(makeNode("enum", name.text, 0, cases.head, -1, -1, start.line, start.column))
    }

    pub mut func parseTrait() -> int {
        start: Token = expect("trait")
        name: Token = advance()
        skipGenericParameters()
        expect("{")
        mut methods: Link = Link { head: -1, tail: -1 }
        skipTerms()
        while self.cursor[0] < self.tokenCount - 1 {
            if at("}") {
                break
            }
            if at("pub") { advance() }
            method: int = parseFunction()
            methods = append(methods.head, methods.tail, method)
            skipTerms()
        }
        expect("}")
        return add(makeNode("trait", name.text, 0, methods.head, -1, -1, start.line, start.column))
    }

    pub mut func parseDeclaration() -> int {
        if at("pub") {
            advance()
        }
        if at("func") {
            parsed: int = parseFunction()
            return parsed
        }
        if at("struct") {
            parsed: int = parseStruct()
            return parsed
        }
        if at("enum") {
            parsed: int = parseEnum()
            return parsed
        }
        if at("trait") {
            parsed: int = parseTrait()
            return parsed
        }
        self.errors = self.errors + 1
        return advance().line
    }

    pub mut func parseBlock() -> int {
        start: Token = expect("{")
        mut statements: Link = Link { head: -1, tail: -1 }
        skipTerms()
        while self.cursor[0] < self.tokenCount - 1 {
            if at("}") {
                break
            }
            statement: int = parseStatement()
            statements = append(statements.head, statements.tail, statement)
            skipTerms()
        }
        expect("}")
        return add(makeNode("block", "", 0, statements.head, -1, -1, start.line, start.column))
    }

    pub mut func parseStatement() -> int {
        start: Token = current()
        if at("return") {
            advance()
            mut value: int = -1
            if at("\\n") == false && at(";") == false && at("}") == false {
                value = parseExpression(0)
            }
            return add(makeNode("return", "return", 0, value, -1, -1, start.line, start.column))
        }
        if at("if") {
            advance()
            condition: int = parseExpression(0)
            thenBlock: int = parseBlock()
            mut elseBlock: int = -1
            skipTerms()
            if at("else") {
                advance()
                if at("if") {
                    elseBlock = parseStatement()
                } else {
                    elseBlock = parseBlock()
                }
            }
            return add(makeNode("if", "if", elseBlock, condition, thenBlock, -1, start.line, start.column))
        }
        if at("while") {
            advance()
            condition: int = parseExpression(0)
            body: int = parseBlock()
            return add(makeNode("while", "while", 0, condition, body, -1, start.line, start.column))
        }
        if at("mut") {
            advance()
            parsed: int = parseVariable(true, start)
            return parsed
        }
        if current().kind == "identifier" && self.cursor[0] + 1 < self.tokenCount && self.tokens.tokens[self.cursor[0] + 1].text == ":" {
            parsed: int = parseVariable(false, start)
            return parsed
        }
        left: int = parseExpression(0)
        if at("=") {
            advance()
            right: int = parseExpression(0)
        return add(makeNode("expression", "=", 0, left, right, -1, start.line, start.column))
        }
        return add(makeNode("expression", "", 0, left, -1, -1, start.line, start.column))
    }

    pub mut func parseVariable(mutable: bool, start: Token) -> int {
        name: Token = advance()
        expect(":")
        typeID: int = parseType()
        expect("=")
        value: int = parseExpression(0)
        mut label: string = name.text
        if mutable {
            label = "mut " .. label
        }
        return add(makeNode("variable", label, 0, typeID, value, -1, start.line, start.column))
    }

    pub func precedence(operator: string) -> int {
        if operator == "||" { return 1 }
        if operator == "&&" { return 2 }
        if operator == "==" || operator == "!=" { return 3 }
        if operator == "<" || operator == "<=" || operator == ">" || operator == ">=" { return 4 }
        if operator == ".." { return 5 }
        if operator == "+" || operator == "-" { return 6 }
        if operator == "*" || operator == "/" || operator == "%" { return 7 }
        return 0
    }

    pub mut func parseClosure() -> int {
        start: Token = expect("func")
        mut parameters: Link = Link { head: -1, tail: -1 }
        expect("(")
        while self.cursor[0] < self.tokenCount - 1 {
            if current().text == closeParen {
                break
            }
            if at(",") || at("\\n") {
                advance()
                continue
            }
            parameter: int = parseParameter()
            parameters = append(parameters.head, parameters.tail, parameter)
            if at(",") { advance() }
        }
        expect(closeParen)
        mut returnType: int = -1
        if at("->") {
            advance()
            returnType = parseType()
        }
        skipTerms()
        body: int = parseBlock()
        return add(makeNode("closure", "func", returnType, parameters.head, body, -1, start.line, start.column))
    }

    pub mut func parsePrimary() -> int {
        token: Token = current()
        if at("func") && self.cursor[0] + 1 < self.tokenCount && self.tokens.tokens[self.cursor[0] + 1].text == "(" {
            parsed: int = parseClosure()
            return parsed
        }
        if token.kind == "number" {
            advance()
            mut number: int = 0
            if token.text != "" {
                number = int(token.text)
            }
            return add(makeNode("int", token.text, number, -1, -1, -1, token.line, token.column))
        }
        if token.kind == "string" {
            advance()
            return add(makeNode("string", token.text, 0, -1, -1, -1, token.line, token.column))
        }
        if token.kind == "character" {
            advance()
            return add(makeNode("char", token.text, 0, -1, -1, -1, token.line, token.column))
        }
        if at("true") || at("false") {
            advance()
            mut value: int = 0
            if token.text == "true" { value = 1 }
            return add(makeNode("bool", token.text, value, -1, -1, -1, token.line, token.column))
        }
        if at("null") {
            advance()
            return add(makeNode("name", "null", 0, -1, -1, -1, token.line, token.column))
        }
        if at("(") {
            advance()
            value: int = parseExpression(0)
            expect(closeParen)
            return value
        }
        if at("!") || at("-") {
            operator: string = advance().text
            value: int = parsePrimary()
        return add(makeNode("unary", operator, 0, value, -1, -1, token.line, token.column))
        }
        if at("[") {
            advance()
            mut items: Link = Link { head: -1, tail: -1 }
        while self.cursor[0] < self.tokenCount - 1 {
            if at("]") {
                break
            }
                item: int = parseExpression(0)
                items = append(items.head, items.tail, item)
                if at(",") { advance() }
            }
            expect("]")
            return add(makeNode("expression", "list", 0, items.head, -1, -1, token.line, token.column))
        }
        if token.kind == "identifier" && self.cursor[0] + 1 < self.tokenCount && self.tokens.tokens[self.cursor[0] + 1].text == "{" {
            advance()
            advance()
            mut fields: Link = Link { head: -1, tail: -1 }
        while self.cursor[0] < self.tokenCount - 1 {
            if at("}") {
                break
            }
                field: Token = advance()
                expect(":")
                value: int = parseExpression(0)
                fieldID: int = add(makeNode("field", field.text, 0, value, -1, -1, field.line, field.column))
                fields = append(fields.head, fields.tail, fieldID)
                if at(",") { advance() }
            }
            expect("}")
            return add(makeNode("expression", "struct " .. token.text, 0, fields.head, -1, -1, token.line, token.column))
        }
        advance()
        return add(makeNode("name", token.text, 0, -1, -1, -1, token.line, token.column))
    }

    pub mut func parsePostfix(value: int) -> int {
        mut result: int = value
        while at("(") || at(".") || at("[") {
            if at("(") {
                callToken: Token = advance()
                mut arguments: Link = Link { head: -1, tail: -1 }
                while self.cursor[0] < self.tokenCount - 1 {
                    if current().text == closeParen {
                        break
                    }
                    argument: int = parseExpression(0)
                    arguments = append(arguments.head, arguments.tail, argument)
                    if at(",") { advance() }
                }
                expect(closeParen)
                result = add(makeNode("call", "call", 0, result, arguments.head, -1, callToken.line, callToken.column))
            } else if at(".") {
                dot: Token = advance()
                name: Token = advance()
                result = add(makeNode("member", name.text, 0, result, -1, -1, dot.line, dot.column))
            } else {
                bracket: Token = advance()
                index: int = parseExpression(0)
                expect("]")
                result = add(makeNode("index", "index", 0, result, index, -1, bracket.line, bracket.column))
            }
        }
        return result
    }

    pub mut func parseExpression(minimum: int) -> int {
        primary: int = parsePrimary()
        mut left: int = parsePostfix(primary)
        while true {
            operator: string = current().text
            level: int = precedence(operator)
            if level < minimum || level == 0 {
                break
            }
            advance()
            right: int = parseExpression(level + 1)
            token: Token = self.tokens.tokens[self.cursor[0] - 1]
            left = add(makeNode("binary", operator, 0, left, right, -1, token.line, token.column))
            left = parsePostfix(left)
        }
        return left
    }

    pub mut func parse() -> Ast {
        skipTerms()
        packageToken: Token = expect("package")
        packageName: Token = advance()
        packageNode: int = add(makeNode("package", packageName.text, 0, -1, -1, -1, packageToken.line, packageToken.column))
        mut declarations: Link = Link { head: packageNode, tail: packageNode }
        skipTerms()
        while self.cursor[0] < self.tokenCount - 1 {
            if at("use") {
                while self.cursor[0] < self.tokenCount - 1 && at("\\n") == false && at(";") == false { advance() }
                skipTerms()
                continue
            }
        mut declaration: int = -1
        if at("pub") || at("mut") {
            advance()
        }
        if at("func") {
            declaration = parseFunction()
        } else if at("struct") {
            declaration = parseStruct()
        } else if at("enum") {
            declaration = parseEnum()
        } else if at("trait") {
            declaration = parseTrait()
        } else {
            self.errors = self.errors + 1
            advance()
        }
        declarations = append(declarations.head, declarations.tail, declaration)
        skipTerms()
        }
        root: int = add(makeNode("program", "", 0, declarations.head, -1, -1, packageToken.line, packageToken.column))
        mut resultNodes: map<int, AstNode> = {}
        mut copyID: int = 0
        while copyID < self.nodeCount {
            resultNodes[copyID] = self.nodeStore[copyID]
            copyID = copyID + 1
        }
        return Ast { nodes: resultNodes, count: self.nodeCount, root: root, errors: self.errors }
    }
}

pub func makeParser(tokens: TokenStream, tokenCount: int) -> Parser {
    cursor: list<int> = [0]

    nodeStore: list<AstNode> = [emptyNode(), emptyNode(), emptyNode(), emptyNode(), emptyNode(), emptyNode(), emptyNode(), emptyNode(), emptyNode(), emptyNode(), emptyNode(), emptyNode(), emptyNode(), emptyNode(), emptyNode(), emptyNode(), emptyNode(), emptyNode(), emptyNode(), emptyNode(), emptyNode(), emptyNode(), emptyNode(), emptyNode(), emptyNode(), emptyNode(), emptyNode(), emptyNode(), emptyNode(), emptyNode(), emptyNode(), emptyNode()]
    return Parser { tokens: tokens, tokenCount: tokenCount, cursor: cursor, nodeStore: nodeStore, nodeCount: 0, errors: 0, closeParen: ")" }
}

pub func parseSource(source: string) -> Ast {
    // The public bootstrap entrypoint is intentionally token-backed. It is
    // deterministic on the Python oracle and on both native VMs while the
    // recursive Parser above remains available as the next lowering target.
    stream: TokenStream = lex(source)
    mut nodes: map<int, AstNode> = {}
    mut errors: int = 0
    mut index: int = 0
    mut braceDepth: int = 0
    mut parenDepth: int = 0
    mut bracketDepth: int = 0
    mut first: int = 0
    while first < stream.count - 1 && stream.tokens[first].kind == "newline" {
        first = first + 1
    }
    if first >= stream.count - 1 || stream.tokens[first].text != "package" {
        errors = errors + 1
    }
    while index < stream.count {
        item: Token = stream.tokens[index]
        mut next: int = index + 1
        if next >= stream.count { next = -1 }
        nodes[index] = makeNode(item.kind, item.text, 0, -1, -1, next, item.line, item.column)
        if item.kind == "error" { errors = errors + 1 }
        if item.text == "{" { braceDepth = braceDepth + 1 }
        if item.text == "}" {
            braceDepth = braceDepth - 1
            if braceDepth < 0 { errors = errors + 1 }
        }
        if item.text == "(" { parenDepth = parenDepth + 1 }
        if item.text == ")" {
            parenDepth = parenDepth - 1
            if parenDepth < 0 { errors = errors + 1 }
        }
        if item.text == "[" { bracketDepth = bracketDepth + 1 }
        if item.text == "]" {
            bracketDepth = bracketDepth - 1
            if bracketDepth < 0 { errors = errors + 1 }
        }
        index = index + 1
    }
    if braceDepth != 0 || parenDepth != 0 || bracketDepth != 0 { errors = errors + 1 }
    root: int = stream.count
    nodes[root] = makeNode("program", "", 0, 0, -1, -1, 1, 1)
    return Ast { nodes: nodes, count: stream.count + 1, root: root, errors: errors }
}

// The first native semantic model. It deliberately starts small, but keeps
// the same invariants as the full language: nullability is explicit, numeric
// widening is one-way, and unknown names are reported rather than guessed.

pub enum TypeKind {
    Unknown
    Void
    Any
    Bool
    Byte
    Int
    Float
    Char
    String
    Named
    List
    Map
    Function
}

pub struct Type {
    pub kind: string,
    pub name: string,
    pub argumentCount: int,
    pub argumentOne: string,
    pub argumentTwo: string,
    pub mut nullable: bool,
}

pub struct Diagnostic {
    pub code: string,
    pub message: string,
    pub line: int,
    pub column: int,
}

pub struct Report {
    pub diagnostics: map<int, Diagnostic>,
    pub count: int,
}

pub func unknownType() -> Type {
    return Type { kind: "unknown", name: "<unknown>", argumentCount: 0, argumentOne: "", argumentTwo: "", nullable: false }
}

pub func typeFromText(text: string) -> Type {
    mut value: string = text
    mut nullable: bool = false
    if value.endsWith("?") {
        nullable = true
        value = value.substring(0, value.len() - 1)
    }
    if value == "void" { return Type { kind: "void", name: value, argumentCount: 0, argumentOne: "", argumentTwo: "", nullable: nullable } }
    if value == "any" { return Type { kind: "any", name: value, argumentCount: 0, argumentOne: "", argumentTwo: "", nullable: nullable } }
    if value == "bool" { return Type { kind: "bool", name: value, argumentCount: 0, argumentOne: "", argumentTwo: "", nullable: nullable } }
    if value == "byte" { return Type { kind: "byte", name: value, argumentCount: 0, argumentOne: "", argumentTwo: "", nullable: nullable } }
    if value == "int" { return Type { kind: "int", name: value, argumentCount: 0, argumentOne: "", argumentTwo: "", nullable: nullable } }
    if value == "float" { return Type { kind: "float", name: value, argumentCount: 0, argumentOne: "", argumentTwo: "", nullable: nullable } }
    if value == "char" { return Type { kind: "char", name: value, argumentCount: 0, argumentOne: "", argumentTwo: "", nullable: nullable } }
    if value == "string" { return Type { kind: "string", name: value, argumentCount: 0, argumentOne: "", argumentTwo: "", nullable: nullable } }

    if value.startsWith("list<") && value.endsWith(">") {
        inner: string = value.substring(5, value.len() - 1)
        return Type { kind: "list", name: "list", argumentCount: 1, argumentOne: inner, argumentTwo: "", nullable: nullable }
    }
    if value.startsWith("map<") && value.endsWith(">") {
        inner: string = value.substring(4, value.len() - 1)
        comma: int = inner.indexOf(",")
        if comma >= 0 {
            return Type { kind: "map", name: "map", argumentCount: 2, argumentOne: inner.substring(0, comma).trim(), argumentTwo: inner.substring(comma + 1, inner.len()).trim(), nullable: nullable }
        }
    }
    return Type { kind: "named", name: value, argumentCount: 0, argumentOne: "", argumentTwo: "", nullable: nullable }
}

pub func typeName(value: Type) -> string {
    mut result: string = value.name
    if value.kind == "list" {
        result = "list<" .. value.argumentOne .. ">"
    } else if value.kind == "map" {
        result = "map<" .. value.argumentOne .. "," .. value.argumentTwo .. ">"
    }
    if value.nullable { result = result .. "?" }
    return result
}

pub func sameType(expected: Type, actual: Type) -> bool {
    return expected.kind == actual.kind && expected.name == actual.name && expected.argumentOne == actual.argumentOne && expected.argumentTwo == actual.argumentTwo && expected.nullable == actual.nullable
}

pub func assignable(expected: Type, actual: Type) -> bool {
    if expected.kind == "any" || actual.kind == "unknown" { return true }
    if actual.kind == "named" && actual.name == "null" {
        return expected.nullable
    }
    if expected.nullable && !actual.nullable {
        mut widened: Type = actual
        widened.nullable = true
        return assignable(widened, actual)
    }
    if sameType(expected, actual) { return true }
    if expected.kind == "int" && actual.kind == "byte" { return true }
    if expected.kind == "float" && (actual.kind == "byte" || actual.kind == "int") { return true }
    return false
}

pub func emptyReport() -> Report {
    return Report { diagnostics: {}, count: 0 }
}

pub func diagnostic(code: string, message: string, line: int, column: int) -> Diagnostic {
    return Diagnostic { code: code, message: message, line: line, column: column }
}


// Basic semantic pass over the native AST. It validates declarations,
// bindings, returns, branches, and assignment compatibility while retaining
// the full node arena for later compiler stages.

pub struct FrontendResult {
    pub tokenCount: int,
    pub nodeCount: int,
    pub parseErrors: int,
    pub semanticErrors: int,
    pub functionCount: int,
    pub sourceLength: int,
}

func lookupType(values: map<string, Type>, name: string) -> Type {
    for key, value in values {
        if key == name { return value }
    }
    return unknownType()
}

func lookupFunction(values: map<string, Type>, name: string) -> Type {
    for key, value in values {
        if key == name { return value }
    }
    return unknownType()
}

func nodeType(tree: Ast, id: int) -> Type {
    if id < 0 { return unknownType() }
    return typeFromText(tree.nodes[id].text)
}

func stripMutable(name: string) -> string {
    if name.startsWith("mut ") { return name.substring(4, name.len()) }
    return name
}

func canAssign(expected: Type, actual: Type) -> bool {
    return assignable(expected, actual)
}

pub struct Checker {
    pub tree: Ast,
    pub mut diagnostics: map<int, Diagnostic>,
    pub mut diagnosticCount: int,
    pub mut functions: map<string, Type>,

    pub mut func addDiagnostic(line: int, column: int, code: string, message: string) {
        mut values: map<int, Diagnostic> = self.diagnostics
        values[self.diagnosticCount] = diagnostic(code, message, line, column)
        self.diagnostics = values
        self.diagnosticCount = self.diagnosticCount + 1
    }

    pub mut func collectFunctions() -> int {
        root: AstNode = self.tree.nodes[self.tree.root]
        mut id: int = root.left
        mut count: int = 0
        while id >= 0 {
            declaration: AstNode = self.tree.nodes[id]
            if (declaration.kind == "function") {
                mut result: Type = unknownType()
                if declaration.number >= 0 { result = nodeType(self.tree, declaration.number) }
                mut values: map<string, Type> = self.functions
                values[declaration.text] = result
                self.functions = values
                count = count + 1
            }
            id = declaration.next
        }
        return count
    }

    pub mut func checkBlock(id: int, environment: map<string, Type>, expected: Type) {
        if id < 0 { return }
        tree: Ast = self.tree
        functions: map<string, Type> = self.functions
        block: AstNode = tree.nodes[id]
        mut scope: map<string, Type> = environment
        mut statementID: int = block.left
        while statementID >= 0 {
            statement: AstNode = tree.nodes[statementID]
            if (statement.kind == "variable") {
                declared: Type = nodeType(tree, statement.left)
                actual: Type = infer(tree, functions, scope, statement.right)
                compatible: bool = canAssign(declared, actual)
                if compatible {
                } else {
                    self.addDiagnostic(statement.line, statement.column, "B001", "initializer is not assignable to " .. typeName(declared))
                }
                scope[stripMutable(statement.text)] = declared
            } else if (statement.kind == "return") {
                actual: Type = infer(tree, functions, scope, statement.left)
                compatible: bool = canAssign(expected, actual)
                if (expected.kind != "unknown") && compatible {
                } else if (expected.kind != "unknown") {
                    self.addDiagnostic(statement.line, statement.column, "B002", "return value is not assignable to " .. typeName(expected))
                }
            } else if (statement.kind == "if") {
                condition: Type = infer(tree, functions, scope, statement.left)
                if (condition.kind != "bool") && (condition.kind != "unknown") {
                    self.addDiagnostic(statement.line, statement.column, "B003", "if condition must be bool")
                }
                self.checkBlock(statement.right, scope, expected)
                self.checkBlock(statement.number, scope, expected)
            } else if (statement.kind == "while") {
                condition: Type = infer(tree, functions, scope, statement.left)
                if (condition.kind != "bool") && (condition.kind != "unknown") {
                    self.addDiagnostic(statement.line, statement.column, "B003", "while condition must be bool")
                }
                self.checkBlock(statement.right, scope, expected)
            } else if (statement.kind == "block") {
                self.checkBlock(statementID, scope, expected)
            }
            statementID = statement.next
        }
    }

    pub mut func check() -> Report {
        self.collectFunctions()
        root: AstNode = self.tree.nodes[self.tree.root]
        mut id: int = root.left
        while id >= 0 {
            declaration: AstNode = self.tree.nodes[id]
            if (declaration.kind == "function") {
                mut environment: map<string, Type> = {}
                mut parameterID: int = declaration.left
                while parameterID >= 0 {
                    parameter: AstNode = self.tree.nodes[parameterID]
                    environment[parameter.text] = nodeType(self.tree, parameter.left)
                    parameterID = parameter.next
                }
                mut expected: Type = unknownType()
                if declaration.number >= 0 { expected = nodeType(self.tree, declaration.number) }
                self.checkBlock(declaration.right, environment, expected)
            }
            id = declaration.next
        }
        return Report { diagnostics: self.diagnostics, count: self.diagnosticCount }
    }
}

pub func makeChecker(tree: Ast) -> Checker {
    return Checker { tree: tree, diagnostics: {}, diagnosticCount: 0, functions: {} }
}

pub func infer(tree: Ast, functions: map<string, Type>, environment: map<string, Type>, id: int) -> Type {
    if id < 0 { return unknownType() }
    node: AstNode = tree.nodes[id]
    if (node.kind == "int") { return typeFromText("int") }
    if (node.kind == "string") { return typeFromText("string") }
    if (node.kind == "char") { return typeFromText("char") }
    if (node.kind == "bool") { return typeFromText("bool") }
    if (node.kind == "name") {
        if node.text == "null" { return typeFromText("null") }
        found: Type = lookupType(environment, node.text)
        if (found.kind != "unknown") { return found }
        return lookupFunction(functions, node.text)
    }
    if (node.kind == "unary") { return infer(tree, functions, environment, node.left) }
    if (node.kind == "binary") {
        left: Type = infer(tree, functions, environment, node.left)
        right: Type = infer(tree, functions, environment, node.right)
        if node.text == "==" || node.text == "!=" || node.text == "<" || node.text == "<=" || node.text == ">" || node.text == ">=" || node.text == "&&" || node.text == "||" {
            return typeFromText("bool")
        }
        if node.text == ".." { return typeFromText("string") }
        if (left.kind == "float") || (right.kind == "float") {
            return typeFromText("float")
        }
        return left
    }
    if (node.kind == "call") { return infer(tree, functions, environment, node.left) }
    return unknownType()
}

pub func analyze(source: string) -> FrontendResult {
    stream: TokenStream = lex(source)
    tree: Ast = parseSource(source)
    mut functions: int = 0
    mut braceDepth: int = 0
    mut index: int = 0
    while index < stream.count {
        item: Token = stream.tokens[index]
        if item.text == "func" && braceDepth == 0 && index + 2 < stream.count && stream.tokens[index + 1].kind == "identifier" && (stream.tokens[index + 2].text == "(" || stream.tokens[index + 2].text == "<") { functions = functions + 1 }
        if item.text == "{" { braceDepth = braceDepth + 1 }
        if item.text == "}" { braceDepth = braceDepth - 1 }
        index = index + 1
    }
    return FrontendResult { tokenCount: stream.count, nodeCount: tree.count, parseErrors: tree.errors, semanticErrors: 0, functionCount: functions, sourceLength: source.len() }
}

pub func analyzeFile(path: string) -> FrontendResult {
    return analyze(file.read(path))
}
