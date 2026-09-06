package bootstrap

use file:ast
use file:parser
use file:types
use file:lexer

// Basic semantic pass over the native AST. It validates declarations,
// bindings, returns, branches, and assignment compatibility while retaining
// the full node arena for later compiler stages.

pub struct FrontendResult {
    pub tokenCount: Int,
    pub nodeCount: Int,
    pub parseErrors: Int,
    pub semanticErrors: Int,
    pub functionCount: Int,
    pub sourceLength: Int,
}

func lookupType(values: Map<String, Type>, name: String) -> Type {
    for key, value in values {
        if key == name { return value }
    }
    return unknownType()
}

func lookupFunction(values: Map<String, Type>, name: String) -> Type {
    for key, value in values {
        if key == name { return value }
    }
    return unknownType()
}

func nodeType(tree: Ast, id: Int) -> Type {
    if id < 0 { return unknownType() }
    return typeFromText(tree.nodes[id].text)
}

func stripMutable(name: String) -> String {
    if name.startsWith("mut ") { return name.substring(4, name.len()) }
    return name
}

func canAssign(expected: Type, actual: Type) -> Bool {
    return assignable(expected, actual)
}

pub struct Checker {
    pub tree: Ast,
    pub mut diagnostics: Map<Int, Diagnostic>,
    pub mut diagnosticCount: Int,
    pub mut functions: Map<String, Type>,

    pub mut func addDiagnostic(line: Int, column: Int, code: String, message: String) {
        mut values: Map<Int, Diagnostic> = self.diagnostics
        values[self.diagnosticCount] = diagnostic(code, message, line, column)
        self.diagnostics = values
        self.diagnosticCount = self.diagnosticCount + 1
    }

    pub mut func collectFunctions() -> Int {
        root: AstNode = self.tree.nodes[self.tree.root]
        mut id: Int = root.left
        mut count: Int = 0
        while id >= 0 {
            declaration: AstNode = self.tree.nodes[id]
            if (declaration.kind == "function") {
                mut result: Type = unknownType()
                if declaration.number >= 0 { result = nodeType(self.tree, declaration.number) }
                mut values: Map<String, Type> = self.functions
                values[declaration.text] = result
                self.functions = values
                count = count + 1
            }
            id = declaration.next
        }
        return count
    }

    pub mut func checkBlock(id: Int, environment: Map<String, Type>, expected: Type) {
        if id < 0 { return }
        tree: Ast = self.tree
        functions: Map<String, Type> = self.functions
        block: AstNode = tree.nodes[id]
        mut scope: Map<String, Type> = environment
        mut statementID: Int = block.left
        while statementID >= 0 {
            statement: AstNode = tree.nodes[statementID]
            if (statement.kind == "variable") {
                declared: Type = nodeType(tree, statement.left)
                actual: Type = infer(tree, functions, scope, statement.right)
                compatible: Bool = canAssign(declared, actual)
                if compatible {
                } else {
                    self.addDiagnostic(statement.line, statement.column, "B001", "initializer is not assignable to " .. typeName(declared))
                }
                scope[stripMutable(statement.text)] = declared
            } else if (statement.kind == "return") {
                actual: Type = infer(tree, functions, scope, statement.left)
                compatible: Bool = canAssign(expected, actual)
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
        mut id: Int = root.left
        while id >= 0 {
            declaration: AstNode = self.tree.nodes[id]
            if (declaration.kind == "function") {
                mut environment: Map<String, Type> = {}
                mut parameterID: Int = declaration.left
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

pub func infer(tree: Ast, functions: Map<String, Type>, environment: Map<String, Type>, id: Int) -> Type {
    if id < 0 { return unknownType() }
    node: AstNode = tree.nodes[id]
    if (node.kind == "int") { return typeFromText("Int") }
    if (node.kind == "string") { return typeFromText("String") }
    if (node.kind == "char") { return typeFromText("Char") }
    if (node.kind == "bool") { return typeFromText("Bool") }
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
            return typeFromText("Bool")
        }
        if node.text == ".." { return typeFromText("String") }
        if (left.kind == "float") || (right.kind == "float") {
            return typeFromText("Float")
        }
        return left
    }
    if (node.kind == "call") { return infer(tree, functions, environment, node.left) }
    return unknownType()
}

pub func analyze(source: String) -> FrontendResult {
    stream: TokenStream = lex(source)
    tree: Ast = parseSource(source)
    mut checker: Checker = makeChecker(tree)
    report: Report = checker.check()
    mut functions: Int = 0
    root: AstNode = tree.nodes[tree.root]
    mut id: Int = root.left
    while id >= 0 {
        if (tree.nodes[id].kind == "function") { functions = functions + 1 }
        id = tree.nodes[id].next
    }
    return FrontendResult { tokenCount: stream.count, nodeCount: tree.count, parseErrors: tree.errors, semanticErrors: report.count, functionCount: functions, sourceLength: source.len() }
}

pub func analyzeFile(path: String) -> FrontendResult {
    return analyze(file.read(path))
}
