package builtin_arity

// Zero-argument builtins must reject extra arguments with a stable,
// catchable message on every backend (no host-specific text).

func probe(label: String, action: Func<Void>) -> String {
    try {
        action()
        return label .. ":no-error"
    } catch (e: Exception) {
        return label .. ":" .. e.message
    }
}

func main() -> Int {
    r1: String = probe("mutex", func() { mutex(5) })
    r2: String = probe("stack", func() { stack(5) })
    r3: String = probe("args", func() { args(5) })
    r4: String = probe("semaphore", func() { semaphore("x") })
    test.assertEq(r1, "mutex:mutex expects no arguments")
    test.assertEq(r2, "stack:stack expects no arguments")
    test.assertEq(r3, "args:args expects no arguments")
    test.assertEq(r4, "semaphore:semaphore expects an Int count")
    println("builtin arity errors passed")
    return 0
}
