package bootstrap

use file:token
use file:ast
use file:lexer

// A recursive-descent/Pratt parser whose mutable cursor and node arena are
// implemented with Solvik struct fields and mutating methods.

pub struct Link {
    pub mut head: Int,
    pub mut tail: Int,
}

pub struct Parser {
    pub tokens: TokenStream,
    pub mut position: Int,
    pub mut nodes: Map<Int, AstNode>,
    pub mut nodeCount: Int,
    pub mut errors: Int,
    pub closeParen: String,

    pub func current() -> Token {
        return self.tokens.tokens[self.position]
    }

    pub func at(text: String) -> Bool {
        return current().text == text
    }

    pub func atEnd() -> Bool {
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

    pub mut func expect(text: String) -> Token {
        if at(text) == false {
            self.errors = self.errors + 1
            if atEnd() == false {
                return advance()
            }
            return current()
        }
        return advance()
    }

    pub mut func add(item: AstNode) -> Int {
        id: Int = self.nodeCount
        mut arena: Map<Int, AstNode> = self.nodes
        arena[id] = item
        self.nodes = arena
        self.nodeCount = self.nodeCount + 1
        return id
    }

    pub mut func append(head: Int, tail: Int, item: Int) -> Link {
        mut result: Link = Link { head: head, tail: tail }
        if head < 0 {
            result.head = item
        } else {
            mut arena: Map<Int, AstNode> = self.nodes
            previous: AstNode = arena[tail]
            previous.next = item
            arena[tail] = previous
            self.nodes = arena
        }
        result.tail = item
        return result
    }

    pub mut func parseType() -> Int {
        first: Token = advance()
        mut text: String = first.text
        mut depth: Int = 0
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

    pub mut func parseParameter() -> Int {
        name: Token = advance()
        expect(":")
        typeID: Int = parseType()
        return add(makeNode("parameter", name.text, 0, typeID, -1, -1, name.line, name.column))
    }

    pub mut func parseFunction() -> Int {
        start: Token = expect("func")
        name: Token = advance()
        mut parameters: Link = Link { head: -1, tail: -1 }
        expect("(")
        while self.position < self.tokens.count - 1 && self.tokens.tokens[self.position].text != closeParen {
            if current().text == "," || current().text == "\\n" {
                advance()
                continue
            }
            parameter: Int = parseParameter()
            parameters = append(parameters.head, parameters.tail, parameter)
            if current().text == "," {
                advance()
            }
        }
        expect(closeParen)
        mut returnType: Int = -1
        if at("->") {
            advance()
            returnType = parseType()
        }
        skipTerms()
        mut body: Int = -1
        if at("{") {
            body = parseBlock()
        }
        return add(makeNode("function", name.text, returnType, parameters.head, body, -1, start.line, start.column))
    }

    pub mut func skipGenericParameters() {
        mut depth: Int = 0
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

    pub mut func parseStruct() -> Int {
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
                method: Int = parseFunction()
                methods = append(methods.head, methods.tail, method)
            } else {
                field: Token = advance()
                expect(":")
                typeID: Int = parseType()
                fieldID: Int = add(makeNode("field", field.text, 0, typeID, -1, -1, field.line, field.column))
                fields = append(fields.head, fields.tail, fieldID)
            }
            skipTerms()
        }
        expect("}")
        return add(makeNode("struct", name.text, 0, fields.head, methods.head, -1, start.line, start.column))
    }

    pub mut func parseEnum() -> Int {
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
                    payloadType: Int = parseType()
                    payload = append(payload.head, payload.tail, payloadType)
                    if at(",") { advance() }
                }
                expect(closeParen)
            }
            caseID: Int = add(makeNode("enum_case", caseToken.text, 0, payload.head, -1, -1, caseToken.line, caseToken.column))
            cases = append(cases.head, cases.tail, caseID)
            skipTerms()
        }
        expect("}")
        return add(makeNode("enum", name.text, 0, cases.head, -1, -1, start.line, start.column))
    }

    pub mut func parseTrait() -> Int {
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
            method: Int = parseFunction()
            methods = append(methods.head, methods.tail, method)
            skipTerms()
        }
        expect("}")
        return add(makeNode("trait", name.text, 0, methods.head, -1, -1, start.line, start.column))
    }

    pub mut func parseDeclaration() -> Int {
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

    pub mut func parseBlock() -> Int {
        start: Token = expect("{")
        mut statements: Link = Link { head: -1, tail: -1 }
        skipTerms()
        while self.position < self.tokens.count - 1 {
            if at("}") {
                break
            }
            statement: Int = parseStatement()
            statements = append(statements.head, statements.tail, statement)
            skipTerms()
        }
        expect("}")
        return add(makeNode("block", "", 0, statements.head, -1, -1, start.line, start.column))
    }

    pub mut func parseStatement() -> Int {
        start: Token = current()
        if at("return") {
            advance()
            mut value: Int = -1
            if at("\\n") == false && at(";") == false && at("}") == false {
                value = parseExpression(0)
            }
            return add(makeNode("return", "return", 0, value, -1, -1, start.line, start.column))
        }
        if at("if") {
            advance()
            condition: Int = parseExpression(0)
            thenBlock: Int = parseBlock()
            mut elseBlock: Int = -1
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
            condition: Int = parseExpression(0)
            body: Int = parseBlock()
            return add(makeNode("while", "while", 0, condition, body, -1, start.line, start.column))
        }
        if at("mut") {
            advance()
            return parseVariable(true, start)
        }
        if current().kind == "identifier" && self.position + 1 < self.tokens.count && self.tokens.tokens[self.position + 1].text == ":" {
            return parseVariable(false, start)
        }
        left: Int = parseExpression(0)
        if at("=") {
            advance()
            right: Int = parseExpression(0)
        return add(makeNode("expression", "=", 0, left, right, -1, start.line, start.column))
        }
        return add(makeNode("expression", "", 0, left, -1, -1, start.line, start.column))
    }

    pub mut func parseVariable(mutable: Bool, start: Token) -> Int {
        name: Token = advance()
        expect(":")
        typeID: Int = parseType()
        expect("=")
        value: Int = parseExpression(0)
        mut label: String = name.text
        if mutable {
            label = "mut " .. label
        }
        return add(makeNode("variable", label, 0, typeID, value, -1, start.line, start.column))
    }

    pub func precedence(operator: String) -> Int {
        if operator == "||" { return 1 }
        if operator == "&&" { return 2 }
        if operator == "==" || operator == "!=" { return 3 }
        if operator == "<" || operator == "<=" || operator == ">" || operator == ">=" { return 4 }
        if operator == ".." { return 5 }
        if operator == "+" || operator == "-" { return 6 }
        if operator == "*" || operator == "/" || operator == "%" { return 7 }
        return 0
    }

    pub mut func parseClosure() -> Int {
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
            parameter: Int = parseParameter()
            parameters = append(parameters.head, parameters.tail, parameter)
            if at(",") { advance() }
        }
        expect(closeParen)
        mut returnType: Int = -1
        if at("->") {
            advance()
            returnType = parseType()
        }
        skipTerms()
        body: Int = parseBlock()
        return add(makeNode("closure", "func", returnType, parameters.head, body, -1, start.line, start.column))
    }

    pub mut func parsePrimary() -> Int {
        token: Token = current()
        if at("func") && self.position + 1 < self.tokens.count && self.tokens.tokens[self.position + 1].text == "(" {
            return parseClosure()
        }
        if token.kind == "number" {
            advance()
            mut number: Int = 0
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
            mut value: Int = 0
            if token.text == "true" { value = 1 }
            return add(makeNode("bool", token.text, value, -1, -1, -1, token.line, token.column))
        }
        if at("null") {
            advance()
            return add(makeNode("name", "null", 0, -1, -1, -1, token.line, token.column))
        }
        if at("(") {
            advance()
            value: Int = parseExpression(0)
            expect(closeParen)
            return value
        }
        if at("!") || at("-") {
            operator: String = advance().text
            value: Int = parsePrimary()
        return add(makeNode("unary", operator, 0, value, -1, -1, token.line, token.column))
        }
        if at("[") {
            advance()
            mut items: Link = Link { head: -1, tail: -1 }
        while self.position < self.tokens.count - 1 {
            if at("]") {
                break
            }
                item: Int = parseExpression(0)
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
                value: Int = parseExpression(0)
                fieldID: Int = add(makeNode("field", field.text, 0, value, -1, -1, field.line, field.column))
                fields = append(fields.head, fields.tail, fieldID)
                if at(",") { advance() }
            }
            expect("}")
            return add(makeNode("expression", "struct " .. token.text, 0, fields.head, -1, -1, token.line, token.column))
        }
        advance()
        return add(makeNode("name", token.text, 0, -1, -1, -1, token.line, token.column))
    }

    pub mut func parsePostfix(value: Int) -> Int {
        mut result: Int = value
        while at("(") || at(".") || at("[") {
            if at("(") {
                callToken: Token = advance()
                mut arguments: Link = Link { head: -1, tail: -1 }
                while self.position < self.tokens.count - 1 {
                    if current().text == closeParen {
                        break
                    }
                    argument: Int = parseExpression(0)
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
                index: Int = parseExpression(0)
                expect("]")
                result = add(makeNode("index", "index", 0, result, index, -1, bracket.line, bracket.column))
            }
        }
        return result
    }

    pub mut func parseExpression(minimum: Int) -> Int {
        mut left: Int = parsePostfix(parsePrimary())
        while true {
            operator: String = current().text
            level: Int = precedence(operator)
            if level < minimum || level == 0 {
                break
            }
            advance()
            right: Int = parseExpression(level + 1)
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
        packageNode: Int = add(makeNode("package", packageName.text, 0, -1, -1, -1, packageToken.line, packageToken.column))
        mut declarations: Link = Link { head: packageNode, tail: packageNode }
        skipTerms()
        while self.position < self.tokens.count - 1 {
            if at("use") {
                while self.position < self.tokens.count - 1 && at("\\n") == false && at(";") == false { advance() }
                skipTerms()
                continue
            }
        declaration: Int = parseDeclaration()
        declarations = append(declarations.head, declarations.tail, declaration)
        skipTerms()
        }
        root: Int = add(makeNode("program", "", 0, declarations.head, -1, -1, packageToken.line, packageToken.column))
        return Ast { nodes: self.nodes, count: self.nodeCount, root: root, errors: self.errors }
    }
}

pub func makeParser(tokens: TokenStream) -> Parser {
    return Parser { tokens: tokens, position: 0, nodes: {}, nodeCount: 0, errors: 0, closeParen: ")" }
}

pub func parseSource(source: String) -> Ast {
    tokens: TokenStream = lex(source)
    mut parser: Parser = makeParser(tokens)
    return parser.parse()
}
