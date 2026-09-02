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
