// A `Result<T, E>` declared inside a named module receives the synthesized Result operations and
// the postfix `?` propagation operator (docs/LANGUAGE_SPEC.md sections 12, 20, 23). The propagation
// and operation lowering resolve the success and error variants through the operand's nominal type,
// not the plain registry name, because a module declaration is registered under a qualified key.

module result_in_module

enum Result<T, E> {
    Ok(T)
    Err(E)
}

func parse(raw: String): Result<Integer, String> {
    if (raw == "bad") {
        return Result.Err("cannot parse")
    }
    return Result.Ok(42)
}

func parseDoubled(raw: String): Result<Integer, String> {
    val value = parse(raw)?
    return Result.Ok(value + value)
}

val good = parse("ok")
println(good.isOk())
println(good.unwrap())
val bad = parse("bad")
println(bad.isErr())
println(bad.unwrapErr())
good.ignore()
bad.ignore()
println(parseDoubled("ok").unwrap())
println(parseDoubled("bad").unwrapErr())
