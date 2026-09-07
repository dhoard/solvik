package reference_type_names

struct HTTPResult {
    pub value: Int
}

enum State {
    Ready
}

func identity<T>(value: T) -> T {
    return value
}

func main() -> Int {
    flag: Bool = bool("true")
    octet: Byte = byte(7)
    number: Int = int("42")
    fraction: Float = float("1.5")
    letter: Char = 'A'
    text: String = string(number)
    values: List<Int> = [number]
    pairs: Map<String, Int> = {"value": number}
    pending: Stack<Int> = Stack.new()
    worker: Thread = Thread.new(ThreadDef { body: func() -> Int { return 0 } })
    worker.start()
    guard: Mutex = Mutex.new()
    gate: Semaphore = Semaphore.new(1)
    child: Process = Process.new(ProcessDef { program: "/bin/sh", args: ["-c", "exit 0"] })
    child.start()
    pattern: Regex = Regex.new("value")
    erased: Any = number
    nullable: Int? = null
    callback: Func<Int, Int> = func(value: Int) -> Int { return value + 1 }
    action: Func<Void> = func() {}
    nested: List<Map<String, Int?>> = [{"value": nullable}]
    result: HTTPResult = HTTPResult { value: identity<Int>(callback(number)) }
    state: State = State.Ready
    test.assertEq(typeOf(flag), "Bool")
    test.assertEq(typeOf(octet), "Byte")
    test.assertEq(typeOf(number), "Int")
    test.assertEq(typeOf(fraction), "Float")
    test.assertEq(typeOf(letter), "Char")
    test.assertEq(typeOf(text), "String")
    test.assertEq(typeOf(values), "List")
    test.assertEq(typeOf(pairs), "Map")
    test.assertEq(typeOf(pending), "Stack")
    test.assertEq(typeOf(worker), "Thread")
    test.assertEq(typeOf(guard), "Mutex")
    test.assertEq(typeOf(gate), "Semaphore")
    test.assertEq(typeOf(child), "Process")
    test.assertEq(typeOf(child.stdout), "InStream")
    test.assertEq(typeOf(child.stdin), "OutStream")
    test.assertEq(typeOf(pattern), "Regex")
    test.assertEq(typeOf(erased), "Int")
    test.assertEq(typeOf(nullable), "null")
    test.assertEq(typeOf(callback), "Func")
    test.assertEq(typeOf(action), "Func")
    test.assertEq(typeOf(result), "HTTPResult")
    test.assertEq(typeOf(state), "State")
    test.assertTrue(isType(result, "HTTPResult"))
    test.assertFalse(isType(result, "httpresult"))
    test.assertTrue(isType(number, "Int"))
    test.assertFalse(isType(number, "int"))
    test.assertEq(string.repeat("ok", 2), "okok")
    action()
    try {
        throw "caught"
    } catch (error: Exception) {
        test.assertEq(typeOf(error), "Exception")
    }
    worker.join()
    gate.acquire()
    gate.release()
    child.stdin.close()
    child.join()
    println("canonical type names passed")
    return 0
}
