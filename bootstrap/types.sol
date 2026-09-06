package bootstrap

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
    pub kind: String,
    pub name: String,
    pub argumentCount: Int,
    pub argumentOne: String,
    pub argumentTwo: String,
    pub mut nullable: Bool,
}

pub struct Diagnostic {
    pub code: String,
    pub message: String,
    pub line: Int,
    pub column: Int,
}

pub struct Report {
    pub diagnostics: Map<Int, Diagnostic>,
    pub count: Int,
}

pub func unknownType() -> Type {
    return Type { kind: "unknown", name: "<unknown>", argumentCount: 0, argumentOne: "", argumentTwo: "", nullable: false }
}

pub func typeFromText(text: String) -> Type {
    mut value: String = text
    mut nullable: Bool = false
    if value.endsWith("?") {
        nullable = true
        value = value.substring(0, value.len() - 1)
    }
    if value == "Void" { return Type { kind: "void", name: value, argumentCount: 0, argumentOne: "", argumentTwo: "", nullable: nullable } }
    if value == "Any" { return Type { kind: "any", name: value, argumentCount: 0, argumentOne: "", argumentTwo: "", nullable: nullable } }
    if value == "Bool" { return Type { kind: "bool", name: value, argumentCount: 0, argumentOne: "", argumentTwo: "", nullable: nullable } }
    if value == "Byte" { return Type { kind: "byte", name: value, argumentCount: 0, argumentOne: "", argumentTwo: "", nullable: nullable } }
    if value == "Int" { return Type { kind: "int", name: value, argumentCount: 0, argumentOne: "", argumentTwo: "", nullable: nullable } }
    if value == "Float" { return Type { kind: "float", name: value, argumentCount: 0, argumentOne: "", argumentTwo: "", nullable: nullable } }
    if value == "Char" { return Type { kind: "char", name: value, argumentCount: 0, argumentOne: "", argumentTwo: "", nullable: nullable } }
    if value == "String" { return Type { kind: "string", name: value, argumentCount: 0, argumentOne: "", argumentTwo: "", nullable: nullable } }

    if value.startsWith("List<") && value.endsWith(">") {
        inner: String = value.substring(5, value.len() - 1)
        return Type { kind: "list", name: "List", argumentCount: 1, argumentOne: inner, argumentTwo: "", nullable: nullable }
    }
    if value.startsWith("Map<") && value.endsWith(">") {
        inner: String = value.substring(4, value.len() - 1)
        comma: Int = inner.indexOf(",")
        if comma >= 0 {
            return Type { kind: "map", name: "Map", argumentCount: 2, argumentOne: inner.substring(0, comma).trim(), argumentTwo: inner.substring(comma + 1, inner.len()).trim(), nullable: nullable }
        }
    }
    return Type { kind: "named", name: value, argumentCount: 0, argumentOne: "", argumentTwo: "", nullable: nullable }
}

pub func typeName(value: Type) -> String {
    mut result: String = value.name
    if value.kind == "list" {
        result = "List<" .. value.argumentOne .. ">"
    } else if value.kind == "map" {
        result = "Map<" .. value.argumentOne .. "," .. value.argumentTwo .. ">"
    }
    if value.nullable { result = result .. "?" }
    return result
}

pub func sameType(expected: Type, actual: Type) -> Bool {
    return expected.kind == actual.kind && expected.name == actual.name && expected.argumentOne == actual.argumentOne && expected.argumentTwo == actual.argumentTwo && expected.nullable == actual.nullable
}

pub func assignable(expected: Type, actual: Type) -> Bool {
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

pub func diagnostic(code: String, message: String, line: Int, column: Int) -> Diagnostic {
    return Diagnostic { code: code, message: message, line: line, column: column }
}
