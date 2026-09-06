package bootstrap_frontend_test

use file:../bootstrap/native

enum Option {
    None
    Some(Int)
}

struct Point {
    pub mut x: Int,
    pub y: Int,
}

func add(a: Int, b: Int) -> Int {
    return a + b
}

func main() -> Int {
    mut source: String = "package demo\nfunc main() -> Int {\nreturn 0\n}\n"
    result: bootstrap_native.FrontendResult = bootstrap_native.analyze(source)
    if result.parseErrors != 0 || result.semanticErrors != 0 {
        return 1
    }
    if result.functionCount != 1 || result.tokenCount < 10 || result.nodeCount < 5 {
        return 2
    }

    modelSource: String = "package model\nenum Option {\nNone\nSome(Int)\n}\nstruct Point {\npub x: Int\n}\nfunc main() -> Int {\nreturn 0\n}\n"
    model: bootstrap_native.FrontendResult = bootstrap_native.analyze(modelSource)
    if model.parseErrors != 0 || model.semanticErrors != 0 || model.nodeCount < 8 {
        return 7
    }

    badSource: String = "package bad\nfunc main() -> Int {\nvalue: String = 1\nreturn 0\n}\n"
    bad: bootstrap_native.FrontendResult = bootstrap_native.analyze(badSource)
    if bad.parseErrors != 0 || bad.tokenCount < 10 {
        return 8
    }

    closureSource: String = "package closure\nfunc make(amount: Int) -> Func<Int, Int> {\nreturn func(value: Int) -> Int {\nreturn value + amount\n}\n}\nfunc main() -> Int {\nreturn 0\n}\n"
    closure: bootstrap_native.FrontendResult = bootstrap_native.analyze(closureSource)
    if closure.parseErrors != 0 || closure.semanticErrors != 0 || closure.functionCount != 2 {
        return 9
    }

    integer: bootstrap_native.Type = bootstrap_native.typeFromText("Int")
    textType: bootstrap_native.Type = bootstrap_native.typeFromText("String")
    floatType: bootstrap_native.Type = bootstrap_native.typeFromText("Float")
    nullableText: bootstrap_native.Type = bootstrap_native.typeFromText("String?")
    if bootstrap_native.assignable(floatType, integer) == false || bootstrap_native.assignable(textType, integer) || nullableText.nullable == false {
        return 3
    }

    values: List<Int> = [1, 2, 3, 4]
    doubled: List<Int> = values.map(func(value: Int) -> Int { return value * 2 })
    if doubled != [2, 4, 6, 8] {
        return 4
    }

    path: String = "bootstrap/token.sol"
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
