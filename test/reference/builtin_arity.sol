package builtin_arity

// Constructor arity must reject wrong argument counts with a stable,
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
    r1: String = probe("mutex", func() { m: Mutex = Mutex.new(5) })
    r2: String = probe("stack", func() { s: Stack<Int> = Stack.new(5) })
    r3: String = probe("args", func() { a: List<String> = args(5) })
    r4: String = probe("semaphore", func() { g: Semaphore = Semaphore.new("x") })
    r5: String = probe("list", func() { xs: List<Int> = List.new(5) })
    r6: String = probe("map", func() { mp: Map<String, Int> = Map.new(5) })
    r7: String = probe("regex", func() { re: Regex = Regex.new() })
    r8: String = probe("exception", func() { e: Exception = Exception.new() })
    test.assertEq(r1, "mutex:Mutex.new expects no arguments")
    test.assertEq(r2, "stack:Stack.new expects no arguments")
    test.assertEq(r3, "args:args expects no arguments")
    test.assertEq(r4, "semaphore:Semaphore.new expects an Int count")
    test.assertEq(r5, "list:List.new expects no arguments")
    test.assertEq(r6, "map:Map.new expects no arguments")
    test.assertEq(r7, "regex:Regex.new expects a String pattern")
    test.assertEq(r8, "exception:Exception.new expects a String message")
    println("builtin arity errors passed")
    return 0
}
