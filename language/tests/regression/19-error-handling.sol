enum Result<T, E> {
    Ok(T)
    Err(E)
}
class ParseError extends RuntimeException { }
func parse(raw: String): Result<Integer, String> {
    if (raw == "bad") {
        return Result.Err("cannot parse")
    }
    return Result.Ok(42)
}
func viaResult(): Result<Integer, String> {
    val value = parse("ok")?
    return Result.Ok(value + 1)
}
val good = parse("ok")
println(good.isOk())
println(good.unwrap())
val bad = parse("bad")
println(bad.isErr())
println(bad.unwrapErr())
println(viaResult().unwrap())
good.ignore()
bad.ignore()
try {
    println("before-throw")
    throw ParseError()
} catch (e: ParseError) {
    println("caught")
} finally {
    println("finally-1")
}
while (true) {
    try {
        println("loop-try")
        break
    } finally {
        println("finally-2")
    }
    println("unreachable")
}

class FirstError extends RuntimeException { }
class SecondError extends RuntimeException { }
try {
    try {
        throw FirstError()
    } finally {
        throw SecondError()
    }
} catch (e: FirstError) {
    println("caught-first")
} catch (e: SecondError) {
    println("caught-second")
}
func pick(): Integer {
    try {
        return 1
    } finally {
        return 2
    }
}
println(pick())
func discard(): Integer {
    try {
        throw FirstError()
    } catch (e: FirstError) {
        throw SecondError()
    } finally {
        return 3
    }
}
println(discard())
class Detailed extends RuntimeException {
    val code: Integer
    Detailed(code: Integer) {
        this.code = code
    }
}
try {
    throw Detailed(7, "detailed message")
} catch (e: Detailed) {
    println("code=" .. e.code)
    println("msg=" .. e.getMessage())
}
try {
    throw SecondError()
} catch (e: SecondError) {
    println("silent=" .. e.getMessage())
}
println("done")
