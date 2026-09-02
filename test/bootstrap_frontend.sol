package bootstrap_frontend_test

use file:../bootstrap/native

enum Option {
    None
    Some(int)
}

struct Point {
    pub mut x: int,
    pub y: int,
}

func add(a: int, b: int) -> int {
    return a + b
}

func main() -> int {
    mut source: string = "package demo\nfunc main() -> int {\nreturn 0\n}\n"
    result: bootstrap_native.FrontendResult = bootstrap_native.analyze(source)
    if result.parseErrors != 0 || result.semanticErrors != 0 {
        return 1
    }
    if result.functionCount != 1 || result.tokenCount < 10 || result.nodeCount < 5 {
        return 2
    }

    modelSource: string = "package model\nenum Option {\nNone\nSome(int)\n}\nstruct Point {\npub x: int\n}\nfunc main() -> int {\nreturn 0\n}\n"
    model: bootstrap_native.FrontendResult = bootstrap_native.analyze(modelSource)
    if model.parseErrors != 0 || model.semanticErrors != 0 || model.nodeCount < 8 {
        return 7
    }

    badSource: string = "package bad\nfunc main() -> int {\nvalue: string = 1\nreturn 0\n}\n"
    bad: bootstrap_native.FrontendResult = bootstrap_native.analyze(badSource)
    if bad.parseErrors != 0 || bad.tokenCount < 10 {
        return 8
    }

    closureSource: string = "package closure\nfunc make(amount: int) -> func<int, int> {\nreturn func(value: int) -> int {\nreturn value + amount\n}\n}\nfunc main() -> int {\nreturn 0\n}\n"
    closure: bootstrap_native.FrontendResult = bootstrap_native.analyze(closureSource)
    if closure.parseErrors != 0 || closure.semanticErrors != 0 || closure.functionCount != 2 {
        return 9
    }

    integer: bootstrap_native.Type = bootstrap_native.typeFromText("int")
    textType: bootstrap_native.Type = bootstrap_native.typeFromText("string")
    floatType: bootstrap_native.Type = bootstrap_native.typeFromText("float")
    nullableText: bootstrap_native.Type = bootstrap_native.typeFromText("string?")
    if bootstrap_native.assignable(floatType, integer) == false || bootstrap_native.assignable(textType, integer) || nullableText.nullable == false {
        return 3
    }

    values: list<int> = [1, 2, 3, 4]
    doubled: list<int> = values.map(func(value: int) -> int { return value * 2 })
    if doubled != [2, 4, 6, 8] {
        return 4
    }

    path: string = "bootstrap/token.sol"
    if !file.exists(path) {
        return 5
    }
    fileResult: bootstrap_native.FrontendResult = bootstrap_native.analyzeFile(path)
    if fileResult.parseErrors != 0 || fileResult.nodeCount < 5 || fileResult.sourceLength <= 0 {
        return 6
    }
    println("phase 11 bootstrap frontend passed")
    return 0
}
