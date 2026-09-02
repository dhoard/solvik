package bootstrap

use file:token
use file:ast
use file:lexer

// A recursive-descent/Pratt parser whose mutable cursor and node arena are
// implemented with Solvik struct fields and mutating methods.

pub struct Link {
    pub mut head: int,
    pub mut tail: int,
}

pub struct Parser {
    pub tokens: TokenStream,
    pub mut position: int,
    pub mut nodes: map<int, AstNode>,
    pub mut nodeCount: int,
    pub mut errors: int,
    pub closeParen: string,

    pub func current() -> Token {
        return self.tokens.tokens[self.position]
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
            self.position = self.position + 1
        }
        return token
    }

    pub mut func skipTerms() {
        while at("\\n") || at(";") || at(",") {
            advance()
        }
    }

    pub mut func expect(text: string) -> Token {
        if at(text) == false {
            self.errors = self.errors + 1
            if atEnd() == false {
                return advance()
            }
            return current()
        }
        return advance()
    }

    pub mut func add(item: AstNode) -> int {
        id: int = self.nodeCount
        mut arena: map<int, AstNode> = self.nodes
        arena[id] = item
        self.nodes = arena
        self.nodeCount = self.nodeCount + 1
        return id
    }

    pub mut func append(head: int, tail: int, item: int) -> Link {
        mut result: Link = Link { head: head, tail: tail }
        if head < 0 {
            result.head = item
        } else {
            mut arena: map<int, AstNode> = self.nodes
            previous: AstNode = arena[tail]
            previous.next = item
            arena[tail] = previous
            self.nodes = arena
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
            while self.position < self.tokens.count - 1 && depth > 0 {
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
        while self.position < self.tokens.count - 1 && self.tokens.tokens[self.position].text != closeParen {
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
            while self.position < self.tokens.count - 1 && depth > 0 {
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
        while self.position < self.tokens.count - 1 {
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
        while self.position < self.tokens.count - 1 {
            if at("}") {
                break
            }
            caseToken: Token = advance()
            mut payload: Link = Link { head: -1, tail: -1 }
            if at("(") {
                advance()
                while self.position < self.tokens.count - 1 {
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
        while self.position < self.tokens.count - 1 {
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
        if at("func") { return parseFunction() }
        if at("struct") { return parseStruct() }
        if at("enum") { return parseEnum() }
        if at("trait") { return parseTrait() }
        self.errors = self.errors + 1
        return advance().line
    }

    pub mut func parseBlock() -> int {
        start: Token = expect("{")
        mut statements: Link = Link { head: -1, tail: -1 }
        skipTerms()
        while self.position < self.tokens.count - 1 {
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
            return parseVariable(true, start)
        }
        if current().kind == "identifier" && self.position + 1 < self.tokens.count && self.tokens.tokens[self.position + 1].text == ":" {
            return parseVariable(false, start)
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
        while self.position < self.tokens.count - 1 {
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
        if at("func") && self.position + 1 < self.tokens.count && self.tokens.tokens[self.position + 1].text == "(" {
            return parseClosure()
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
        while self.position < self.tokens.count - 1 {
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
        if token.kind == "identifier" && self.position + 1 < self.tokens.count && self.tokens.tokens[self.position + 1].text == "{" {
            advance()
            advance()
            mut fields: Link = Link { head: -1, tail: -1 }
        while self.position < self.tokens.count - 1 {
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
                while self.position < self.tokens.count - 1 {
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
        mut left: int = parsePostfix(parsePrimary())
        while true {
            operator: string = current().text
            level: int = precedence(operator)
            if level < minimum || level == 0 {
                break
            }
            advance()
            right: int = parseExpression(level + 1)
            token: Token = self.tokens.tokens[self.position - 1]
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
        while self.position < self.tokens.count - 1 {
            if at("use") {
                while self.position < self.tokens.count - 1 && at("\\n") == false && at(";") == false { advance() }
                skipTerms()
                continue
            }
        declaration: int = parseDeclaration()
        declarations = append(declarations.head, declarations.tail, declaration)
        skipTerms()
        }
        root: int = add(makeNode("program", "", 0, declarations.head, -1, -1, packageToken.line, packageToken.column))
        return Ast { nodes: self.nodes, count: self.nodeCount, root: root, errors: self.errors }
    }
}

pub func makeParser(tokens: TokenStream) -> Parser {
    return Parser { tokens: tokens, position: 0, nodes: {}, nodeCount: 0, errors: 0, closeParen: ")" }
}

pub func parseSource(source: string) -> Ast {
    tokens: TokenStream = lex(source)
    mut parser: Parser = makeParser(tokens)
    return parser.parse()
}
