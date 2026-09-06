// ============================================================================
//  example.sol -- Solvik Language Example
//
//  A complete, deterministic tour of the Solvik language. It runs identically
//  on the Python reference and the Go / Rust bytecode-VM interpreters. Every
//  language construct is exercised here except *remote* package usage
//  (`use url:` and the network http client).
//
//  Run:  solvik example.sol
// ============================================================================

package example

// ----------------------------------------------------------------------------
// Local file dependencies. Only local `use file:` is shown here; remote
// (`use url:`) imports are intentionally excluded from this tour.
// ----------------------------------------------------------------------------
use file:lib.format

// ----------------------------------------------------------------------------
// 1.  Primitive types, conversions, nullability
// ----------------------------------------------------------------------------
func sectionVariables() -> String {
    mut count: Int = 42
    count = count + 1
    million: Int = 1_000_000
    big: Int = 5_000_000_000
    active: Bool = true
    initial: Char = 'A'
    smallByte: Byte = byte(200)
    greeting: String = "hello"
    maybe: String? = null
    fallback: String = maybe ?? "default"

    sumBytes: Int = smallByte + byte(40)
    asStr: String = string(123)
    asInt: Int = int("456") + int(7.9)
    asTrue: Bool = bool(1)
    asFalse: Bool = bool(0)
    typ1: String = typeOf(42)
    typ2: String = typeOf("s")
    isInt: Bool = isType(5, "Int")
    castBack: Byte = byte(7)
    charOrd: Bool = 'a' < 'z'
    intOfChar: Int = int('B')

    return "count=" .. count .. " million=" .. million .. " big=" .. big
        .. " active=" .. active .. " char=" .. string(initial)
        .. " fallback=" .. fallback .. " bytes=" .. sumBytes
        .. " asStr=" .. asStr .. " asInt=" .. asInt
        .. " asTrue=" .. asTrue .. " asFalse=" .. asFalse
        .. " types=" .. typ1 .. "/" .. typ2 .. " isInt=" .. isInt
        .. " castBack=" .. string(castBack) .. " charOrd=" .. charOrd
        .. " intOfChar=" .. intOfChar .. " len=" .. greeting.len()
}

// ----------------------------------------------------------------------------
// 2.  Strings, raw strings, regex, indexing / iteration
// ----------------------------------------------------------------------------
func sectionStrings() -> String {
    esc: String = "a\nb\tt\x41\u0042"
    raw: String = r"C:\path\file.txt"
    quoted: String = r#"He said "hi"."#
    euro: Char = 'é'
    first: Char = "hello"[0]
    sub: String = "Hello, World!".substring(0, 5)
    has: Bool = "Hello".contains("ell")
    up: String = "abc".toUpper()
    low: String = "XYZ".toLower()
    parts: List<String> = "a,b,c".split(",")
    joined: String = string.join(parts, "-")
    pad: String = string.padStart("5", 3, "0")
    rep: String = string.repeat("ab", 3)
    trimmed: String = "  x  ".trim()
    starts: Bool = "Hello".startsWith("He")
    ends: Bool = "Hello".endsWith("lo")
    index: Int = "Hello".indexOf("l")
    byteLen: Int = "é".byteLength()
    mut count: Int = 0
    for c in "hi" {
        count = count + 1
    }
    return esc .. " | " .. raw .. " | " .. quoted
        .. " | euro=" .. string(euro) .. " first=" .. string(first)
        .. " sub=" .. sub .. " has=" .. has
        .. " case=" .. up .. "/" .. low
        .. " joined=" .. joined .. " pad=" .. pad .. " rep=" .. rep
        .. " trim=[" .. trimmed .. "] starts=" .. starts .. " ends=" .. ends
        .. " indexOf=" .. index .. " byteLen=" .. byteLen
        .. " chars=" .. count
}

// ----------------------------------------------------------------------------
// 3.  Operators: arithmetic, comparison, logic, bitwise, coalesce
// ----------------------------------------------------------------------------
func sectionOperators() -> String {
    sum: Int = 10 + 20
    diff: Int = 50 - 15
    prod: Int = 6 * 7
    quot: Int = 100 / 3
    rem: Int = 100 % 3
    bits: Int = 0xFF & 15
    orBits: Int = 0x0F | 0xF0
    xorBits: Int = 0xFF ^ 0x0F
    shifted: Int = 1 << 8
    notBits: Int = ~0
    chain: Int = null ?? null ?? 7
    chooseZero: Int = null ?? 0 ?? 99
    cmp: Bool = (10 + 20) * 2 == 60 && 5 > 3 && !false
    return "sum=" .. sum .. " diff=" .. diff .. " prod=" .. prod
        .. " quot=" .. quot .. " rem=" .. rem
        .. " and=" .. bits .. " or=" .. orBits .. " xor=" .. xorBits
        .. " shl=" .. shifted .. " not=" .. notBits
        .. " chain=" .. chain .. " zero=" .. chooseZero .. " cmp=" .. cmp
}

// ----------------------------------------------------------------------------
// 4.  Control flow: if / else, while, for, break, continue, switch
// ----------------------------------------------------------------------------
func classify(n: Int) -> String {
    if n > 0 {
        return "positive"
    } else if n < 0 {
        return "negative"
    }
    return "zero"
}

func sumWhile(limit: Int) -> Int {
    mut total: Int = 0
    mut i: Int = 0
    while i <= limit {
        total = total + i
        i = i + 1
    }
    return total
}

func sumPositives(values: List<Int>) -> Int {
    mut total: Int = 0
    for v in values {
        if v < 0 {
            continue
        }
        total = total + v
    }
    return total
}

func findFirst(values: List<Int>, needle: Int) -> Int {
    for v in values {
        if v == needle {
            return v
        }
    }
    return -1
}

func statusWord(code: Int) -> String {
    switch code {
        case 200 {
            return "ok"
        }
        case 404 {
            return "missing"
        }
        case 500 {
            return "error"
        }
        default {
            return "other"
        }
    }
}

func logKind(line: String) -> String {
    switch line {
        case regex(r"^ERROR") {
            return "error"
        }
        case "WARN" {
            return "warn"
        }
        default {
            return "other"
        }
    }
}

// ----------------------------------------------------------------------------
// 5.  Functions: recursion, variadics, spread, function types, closures
// ----------------------------------------------------------------------------
func factorial(n: Int) -> Int {
    if n <= 1 {
        return 1
    }
    return n * factorial(n - 1)
}

func totalArgs(values: ...Int) -> Int {
    mut total: Int = 0
    for v in values {
        total = total + v
    }
    return total
}

func apply(value: Int, f: Func<Int, Int>) -> Int {
    return f(value)
}

func makeAdder(amount: Int) -> Func<Int, Int> {
    return func(x: Int) -> Int {
        return x + amount
    }
}

func makeCounter() -> Func<Int> {
    mut count: Int = 0
    return func() -> Int {
        count = count + 1
        return count
    }
}

// `mut` capture shares storage between the closure and the enclosing scope.
func makeShared() -> Func<Int, Int> {
    mut base: Int = 3
    inner: Func<Int, Int> = func(x: Int) -> Int {
        return base + x
    }
    base = 5
    return inner
}

// ----------------------------------------------------------------------------
// 6.  Generics: functions, structs, constraints, higher-order
// ----------------------------------------------------------------------------
func identity<T>(value: T) -> T {
    return value
}

struct Box<T> {
    pub value: T

    pub func get() -> T {
        return value
    }
}

func pick<T>(a: T, b: T, useA: Bool) -> T {
    if useA {
        return a
    }
    return b
}

func applyTwice<T>(value: T, f: Func<T, T>) -> T {
    return f(f(value))
}

// ----------------------------------------------------------------------------
// 7.  Structs: fields, mutability, methods, equality, empty struct
// ----------------------------------------------------------------------------
struct Point {
    pub mut x: Int
    pub mut y: Int

    pub func describe() -> String {
        return "(" .. x .. "," .. y .. ")"
    }

    pub mut func moveBy(dx: Int, dy: Int) {
        x = x + dx
        y = y + dy
    }
}

struct Counter {
    pub mut value: Int
    label: String

    pub mut func increment() {
        value = value + 1
    }

    pub func summary() -> String {
        return label .. "=" .. value
    }
}

struct Empty {}

// ----------------------------------------------------------------------------
// 8.  Enums: simple + payload + generic, pattern matching
// ----------------------------------------------------------------------------
enum Color {
    Red
    Green
    Blue
}

enum Shape {
    Rect(Int, Int)
    Circle(Int)
    Group(Shape)
}

enum Result<T, E> {
    Ok(T)
    Error(E)
}

enum Option<T> {
    Some(T)
    None
}

func shapeArea(s: Shape) -> Int {
    switch s {
        case Shape.Rect(w, h) {
            return w * h
        }
        case Shape.Circle(r) {
            return 3 * r * r
        }
        case Shape.Group(inner) {
            return shapeArea(inner)
        }
    }
}

func unwrap<T>(o: Option<T>, fallback: T) -> T {
    switch o {
        case Option.Some(v) {
            return v
        }
        case Option.None {
            return fallback
        }
    }
}

func describeColor(c: Color) -> String {
    switch c {
        case Color.Red {
            return "red"
        }
        case Color.Green {
            return "green"
        }
        case Color.Blue {
            return "blue"
        }
        default {
            return "?"
        }
    }
}

// ----------------------------------------------------------------------------
// 9.  Traits: structural satisfaction and constraints
// ----------------------------------------------------------------------------
trait Sized<Q> {
    func sized(v: Q) -> Int
}

struct Doubler {
    pub func sized(v: Int) -> Int {
        return v * 2
    }
}

func applySized<Q, C: Sized<Q>>(c: C) -> Int {
    return c.sized(3)
}

func joinAll<T: Stringable, C: Iterable<T>>(items: C) -> String {
    mut out: String = ""
    for v in items {
        out = out .. v.string()
    }
    return out
}

// ----------------------------------------------------------------------------
// 10. Exceptions: throw / try / catch / finally
// ----------------------------------------------------------------------------
func fail(msg: String) -> Int {
    throw msg
}

func withCleanup() -> Int {
    try {
        return 7
    } finally {
        // runs before the value is returned
    }
    return 0
}

// ----------------------------------------------------------------------------
// 11. Concurrency: shared-heap threads with an explicit mutex
// ----------------------------------------------------------------------------
struct Request {
    pub mut value: Int
    pub mut reply: Int
}

func sectionThread() -> String {
    lock: Mutex = mutex()
    req: Request = Request { value: 21, reply: 0 }
    t: Thread = Thread.start(ThreadDef { body: func() -> Int {
        lock.lock()
        try {
            req.reply = req.value * 2
        } finally {
            lock.unlock()
        }
        return 0
    } })
    code: Int = t.join()
    return "reply=" .. req.reply .. " exit=" .. code
}

// ----------------------------------------------------------------------------
// 13. Collections: list/string/stack methods, higher-order closures
// ----------------------------------------------------------------------------
func sectionCollections() -> String {
    xs: List<Int> = [1, 2, 3, 4, 5]
    doubled: List<Int> = xs.map(func(x: Int) -> Int { return x * 2 })
    evens: List<Int> = xs.filter(func(x: Int) -> Bool { return x % 2 == 0 })
    total: Int = xs.fold(0, func(acc: Int, x: Int) -> Int { return acc + x })
    sumAll: Int = xs.reduce(func(a: Int, b: Int) -> Int { return a + b })
    found: Int? = xs.find(func(x: Int) -> Bool { return x > 3 })
    hasBig: Bool = xs.any(func(x: Int) -> Bool { return x > 4 })
    allSmall: Bool = xs.all(func(x: Int) -> Bool { return x < 10 })
    first: Int = xs.first() ?? 0
    last: Int = xs.last() ?? 0
    rev: List<Int> = [3, 1, 2].reverse()
    sorted: List<Int> = [3, 1, 2].sort(func(a: Int, b: Int) -> Int { return a - b })
    strs: List<String> = xs.map(func(x: Int) -> String { return string(x) })
    joined: String = string.join(strs, ",")
    stackX: Stack<Int> = stack()
    stackX.push(1)
    stackX.push(2)
    peek: Int = stackX.peek()
    popped: Int = stackX.pop()
    emptyNow: Bool = stackX.isEmpty()
    return "map0=" .. doubled[0] .. " evens=" .. evens.len()
        .. " fold=" .. total .. " reduce=" .. sumAll
        .. " find=" .. (found ?? -1) .. " any=" .. hasBig .. " all=" .. allSmall
        .. " ends=" .. first .. "," .. last
        .. " rev=" .. rev[0] .. rev[1] .. rev[2]
        .. " sorted=" .. string.join(sorted.map(func(x: Int) -> String { return string(x) }), "")
        .. " join=" .. joined .. " stack=" .. peek .. "," .. popped .. "," .. emptyNow
}

// A struct with a user iterator() method, iterable by `for`.
struct Pair2 {
    pub a: Int
    pub b: Int

    pub func iterator() -> List<Int> {
        return [a, b]
    }
}

func customIterable() -> Int {
    p: Pair2 = Pair2 { a: 5, b: 6 }
    mut total: Int = 0
    for v in p {
        total = total + v
    }
    return total
}

// ----------------------------------------------------------------------------
// 14. Nullability narrowing, any, core traits
// ----------------------------------------------------------------------------
func nullableDemo(v: String?) -> Int {
    if v != null {
        return v.len()
    }
    return -1
}

func anyDemo() -> String {
    a: Any = 42
    b: Any = "text"
    same: Bool = (b == "text")
    down: Bool = isType(a, "Int") && isType(b, "String")
    s: String = b  // any -> string assignment
    n: Int = a     // any -> int assignment
    return string(s) .. "/" .. string(n) .. " same=" .. same .. " down=" .. down
}

// ----------------------------------------------------------------------------
// 15. Statement termination, trailing commas, block scope
// ----------------------------------------------------------------------------
func sectionTermination() -> String {
    a: Int = 1; b: Int = 2; c: Int = 3
    mut inner: Int = 0
    {
        inner = 10
    }
    msg: String = formatMessage(
        "OK",
        "term",
    )
    return string(a + b + c) .. " inner=" .. inner .. " msg=" .. msg
}

func formatMessage(level: String, text: String) -> String {
    return "[" .. level .. "] " .. text
}


// ----------------------------------------------------------------------------
// 12. Deterministic standard library (math/path/base64/hash/json/time/file)
// ----------------------------------------------------------------------------
func sectionStdlib() -> String {
    ma: Int = math.abs(-9)
    mn: Int = math.min(4, 7)
    mx: Int = math.max(4, 7)
    fl: Int = int(math.floor(3.9))
    ce: Int = int(math.ceil(3.1))
    sq: Int = int(math.sqrt(81.0))
    pj: String = path.join("a", "b", "c.txt")
    pb: String = path.basename("/x/y/z.txt")
    pd: String = path.dirname("/x/y/z.txt")
    pe: String = path.ext("archive.tar.gz")
    enc: String = base64.encode("Hello, Solvik!")
    dec: String = base64.decode(enc)
    md5v: String = hash.md5("abc")
    sh256: String = hash.sha256("abc")
    return "math=" .. ma .. "," .. mn .. "," .. mx .. "," .. fl .. "," .. ce .. "," .. sq
        .. " path=" .. pj .. "|" .. pb .. "|" .. pd .. "|" .. pe
        .. " b64=" .. enc .. "|" .. dec .. " md5=" .. md5v .. " sha256=" .. sh256
}

func sectionJson() -> String {
    parsed: Map<String, Any> = json.parse(r#"{"name":"solvik","year":2024,"ok":true}"#)
    name: String = parsed["name"]
    ok: Bool = parsed["ok"]
    return name .. " ok=" .. ok
}

func sectionTime() -> String {
    iso: String = time.iso(1700000000000)
    back: Int = time.parse("2023-11-14T22:13:20Z")
    return iso .. " back=" .. back
}

func sectionFile() -> String {
    exists: Bool = file.exists("example.sol")
    isFile: Bool = file.isFile("example.sol")
    return "exists=" .. exists .. " isFile=" .. isFile
}


// ----------------------------------------------------------------------------
// 17. Shared struct payloads across threads; test module; map iteration
// ----------------------------------------------------------------------------
struct Job {
    id: Int
}

func squarePool() -> String {
    mut jobs: Stack<Job> = stack()
    mut k: Int = 1
    while k <= 3 {
        jobs.push(Job { id: k })
        k = k + 1
    }
    mut results: Map<Int, Int> = {}
    lock: Mutex = mutex()
    worker: Func<Int> = func() -> Int {
        while true {
            mut job: Job? = null
            lock.lock()
            try {
                if jobs.len() > 0 {
                    job = jobs.pop()
                }
            } finally {
                lock.unlock()
            }
            if job != null {
                id: Int = job.id
                lock.lock()
                try {
                    results[id] = id * id
                } finally {
                    lock.unlock()
                }
            } else {
                return 0
            }
        }
        return 0
    }
    a: Thread = Thread.start(ThreadDef { body: worker })
    b: Thread = Thread.start(ThreadDef { body: worker })
    a.join()
    b.join()
    mut sum: Int = 0
    for key, value in results {
        sum = sum + value
    }
    return "sum=" .. sum
}

func mapIteration() -> String {
    single: Map<String, Int> = { "k": 1 }
    mut pair: String = ""
    for key, value in single {
        pair = key .. "=" .. value
    }
    return pair
}

func testModule() -> Int {
    test.assertTrue(3 > 2)
    test.assertEq(2 + 2, 4)
    test.assertNull(null)
    return 0
}


// ----------------------------------------------------------------------------
// 18. Threading: fork-join over shared state guarded by a mutex
// ----------------------------------------------------------------------------
// Threads share the interpreter heap: closures capture bindings by reference,
// so every worker updates the same counter. The mutex makes each
// read-modify-write atomic; join() waits for a worker and returns its result.
// Workers never print -- main() prints, so output is fully deterministic.
func workerPool() -> String {
    mut total: Int = 0
    lock: Mutex = mutex()
    worker: Func<Int> = func() -> Int {
        mut i: Int = 0
        while i < 3 {
            lock.lock()
            try {
                total = total + i
            } finally {
                lock.unlock()
            }
            i = i + 1
        }
        return 4
    }
    a: Thread = Thread.start(ThreadDef { body: worker })
    b: Thread = Thread.start(ThreadDef { body: worker })
    c: Thread = Thread.start(ThreadDef { body: worker })
    ea: Int = a.join()
    eb: Int = b.join()
    ec: Int = c.join()
    done: Bool = a.is_done() && b.is_done() && c.is_done()
    return "total=" .. total .. " exits=" .. ea .. eb .. ec .. " done=" .. done
}

// ----------------------------------------------------------------------------
// 19. External processes: Process.start with standard streams
// ----------------------------------------------------------------------------
// Process.start() launches argv directly (no shell) and wires stdin/stdout/
// stderr to streams. join() waits for exit and returns the child's status;
// buffered output stays readable afterwards.
func sectionProcesses() -> String {
    ok: Process = Process.start(ProcessDef { program: "/bin/sh", args: ["-c", "printf proc-output"] })
    ok.stdin.close()
    out: String? = ok.stdout.readLine()
    okStatus: Int = ok.join()
    fail: Process = Process.start(ProcessDef { program: "/bin/false", args: [] })
    fail.stdin.close()
    badStatus: Int = fail.join()
    return "ok=" .. okStatus .. " stdout=" .. (out ?? "") .. " fail=" .. badStatus
}

func main() -> Int {
    // use: dependency package
    println("use=" .. format.greetFromLib("Solvik"))

    println("1-vars=" .. sectionVariables())
    println("2-strs=" .. sectionStrings())
    println("3-ops=" .. sectionOperators())

    println("4-cond=" .. classify(3) .. "," .. classify(-1) .. "," .. classify(0))
    println("4-while=" .. sumWhile(10))
    println("4-for=" .. sumPositives([10, -5, 20, -8, 30]))
    println("4-break=" .. findFirst([1, 2, 9, 4], 9))
    println("4-switch=" .. statusWord(200) .. "," .. statusWord(500) .. "," .. statusWord(7))
    println("4-regex=" .. logKind("ERROR boom") .. "," .. logKind("WARN") .. "," .. logKind("x"))

    println("5-factorial=" .. factorial(10))
    println("5-variadic=" .. totalArgs() .. "," .. totalArgs(1, 2, 3))
    nums: List<Int> = [4, 5, 6]
    println("5-spread=" .. totalArgs(nums...))
    add5: Func<Int, Int> = makeAdder(5)
    println("5-closure=" .. apply(10, add5))
    counter: Func<Int> = makeCounter()
    println("5-counter=" .. counter() .. "," .. counter())
    shared: Func<Int, Int> = makeShared()
    println("5-shared=" .. shared(1))

    println("6-generic=" .. identity(42) .. "," .. identity("s"))
    println("6-box=" .. Box<Int> { value: 7 }.get())
    println("6-pick=" .. pick(1, 2, true) .. "," .. pick("a", "b", false))
    println("6-twice=" .. applyTwice(2, func(x: Int) -> Int { return x * 3 }))

    mut p: Point = Point { x: 3, y: 4 }
    p.moveBy(1, 2)
    println("7-point=" .. p.describe())
    mut c: Counter = Counter { value: 0, label: "n" }
    c.increment()
    c.increment()
    println("7-counter=" .. c.summary())
    e: Empty = Empty {}
    println("7-empty=ok")
    println("7-equality=" .. (Point { x: 1, y: 2 } == Point { x: 1, y: 2 }))

    println("8-color=" .. describeColor(Color.Blue) .. " int=" .. int(Color.Green))
    println("8-area=" .. shapeArea(Shape.Rect(3, 4)) .. "," .. shapeArea(Shape.Circle(2)) .. "," .. shapeArea(Shape.Group(Shape.Rect(1, 2))))
    r: Result<Int, String> = Result<Int, String>.Ok(5)
    println("8-result=" .. unwrap(Option<Int>.Some(9), -1) .. "," .. unwrap(Option<Int>.None, -1))

    println("9-sized=" .. applySized(Doubler { }))
    println("9-join=" .. joinAll([1, 2, 3]))

    try {
        fail("oops")
    } catch (err: Exception) {
        println("10-catch=" .. err.message)
    }
    try {
        x: Int = 1
        y: Int = 0
        z: Int = x / y
        println("10-unreached=" .. z)
    } catch (err: Exception) {
        println("10-divzero=" .. err.message)
    }
    println("10-finally=" .. withCleanup())

    println("11-thread=" .. sectionThread())
    println("13-colls=" .. sectionCollections())
    println("13-iter=" .. customIterable())
    println("14-null=" .. nullableDemo(null) .. "," .. nullableDemo("hi"))
    println("14-any=" .. anyDemo())
    println("15-term=" .. sectionTermination())
    println("12-stdlib=" .. sectionStdlib())
    println("12-json=" .. sectionJson())
    println("12-time=" .. sectionTime())
    println("12-file=" .. sectionFile())
    println("17-mapiter=" .. mapIteration())
    println("17-test=" .. testModule())
    println("17-explicit=" .. identity<Int>(42) .. "," .. Box<Box<Int>> { value: Box<Int> { value: 6 } }.value.value)
    println("17-shared=" .. squarePool())

    println("18-thread=" .. workerPool())
    println("19-proc=" .. sectionProcesses())


    return 0
}
