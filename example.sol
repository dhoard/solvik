// ============================================================================
//  example.sol -- Solvik Language Example
//
//  A complete, deterministic tour of the Solvik language. It runs identically
//  on the Python reference and the Go / Rust bytecode-VM interpreters. Every
//  language construct is exercised here except remote package usage
//  (`use url:`). The http client is exercised through a guaranteed-refused
//  loopback address inside try/catch so its output stays deterministic
//  offline. Seeded random draw sequences are backend-specific, so `random`
//  and `secrets` are exercised by property only.
//
//  Run:  solvik example.sol
// ============================================================================

/* Nested block comments are legal and may nest: /* inner */ outer */

package example

// ----------------------------------------------------------------------------
// Local file dependencies. Only local `use file:` is shown here; remote
// (`use url:`) imports are intentionally excluded from this tour.
// ----------------------------------------------------------------------------
use file:lib.format
use file:lib.tour

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
    pi: Float = 3.14159
    doubledPi: Float = pi * 2.0
    floatBigger: Bool = doubledPi > pi
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
        .. " float=" .. doubledPi .. " floatCmp=" .. floatBigger
        .. " types=" .. typ1 .. "/" .. typ2 .. " isInt=" .. isInt
        .. " castBack=" .. string(castBack) .. " charOrd=" .. charOrd
        .. " intOfChar=" .. intOfChar .. " len=" .. greeting.len()
}

// ----------------------------------------------------------------------------
// 2.  Strings, raw strings, regex, indexing / iteration
// ----------------------------------------------------------------------------
func sectionStrings() -> String {
    esc: String = "a\nb\tt\x41\u0042"
    nul: String = "a\0b"
    astral: String = "\U0001F600"
    raw: String = r"C:\path\file.txt"
    quoted: String = r#"He said "hi"."#
    euro: Char = 'é'
    tabChar: Char = '\t'
    expLit: Float = 1.5e1_0
    first: Char = "hello"[0]
    at: Char = "héllo".charAt(1)
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
        .. " | euro=" .. string(euro) .. " tab=" .. string(tabChar)
        .. " nulLen=" .. nul.len() .. " astralLen=" .. astral.len()
        .. " expLit=" .. expLit .. " at=" .. string(at)
        .. " first=" .. string(first)
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
    shiftedRight: Int = 0x1FF0 >> 4
    binLit: Int = 0b1010
    octLit: Int = 0o17
    notBits: Int = ~0
    fmod: Float = 5.5 % 2.0
    chain: Int = null ?? null ?? 7
    chooseZero: Int = null ?? 0 ?? 99
    cmp: Bool = (10 + 20) * 2 == 60 && 5 > 3 && !false
    return "sum=" .. sum .. " diff=" .. diff .. " prod=" .. prod
        .. " quot=" .. quot .. " rem=" .. rem
        .. " and=" .. bits .. " or=" .. orBits .. " xor=" .. xorBits
        .. " shl=" .. shifted .. " shr=" .. shiftedRight
        .. " bin=" .. binLit .. " oct=" .. octLit .. " not=" .. notBits
        .. " fmod=" .. fmod
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

// break leaves the innermost loop; continue skips to the next iteration.
func firstAbove(values: List<Int>, target: Int) -> Int {
    mut i: Int = 0
    while i < values.len() {
        if values[i] > target {
            break
        }
        i = i + 1
    }
    return i
}

// A wider numeric case matches a narrower switch value: case 1 matches 1.0.
func floatWord(v: Float) -> String {
    switch v {
        case 1 {
            return "one"
        }
        case 2 {
            return "two"
        }
        default {
            return "other"
        }
    }
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
        case Regex.new(r"^ERROR") {
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

// A source function with no arrow returns Void.
func ensurePositive(n: Int) {
    if n <= 0 {
        throw "non-positive"
    }
}

func runVoid() -> String {
    mut c: Counter = Counter { value: 0, label: "v" }
    bump: Func<Void> = func() {
        c.increment()
    }
    bump()
    bump()
    ensurePositive(c.value)
    return c.summary()
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

    // Type-associated function (static method): a factory with no receiver.
    // `new` is a convention, not a requirement — any name works.
    pub static func origin() -> Point {
        return Point { x: 0, y: 0 }
    }

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

// Integer-backed enums may declare explicit values.
enum Level {
    Low = 1
    High = 7
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

// Nested patterns destructure payloads recursively; `_` is the payload
// wildcard (a bare `case _` is not a pattern; use `default`).
enum Expr {
    Num(Int)
    Add(Expr, Expr)
}

func eval(e: Expr) -> Int {
    switch e {
        case Expr.Num(n) {
            return n
        }
        case Expr.Add(Expr.Num(l), Expr.Num(r)) {
            return l + r
        }
        default {
            return -1
        }
    }
}

func isGroupOfCircles(s: Shape) -> Bool {
    switch s {
        case Shape.Group(Shape.Circle(_)) {
            return true
        }
        default {
            return false
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
    lock: Mutex = Mutex.new()
    req: Request = Request { value: 21, reply: 0 }
    t: Thread = Thread.new(ThreadDef { body: func() -> Int {
        lock.lock()
        try {
            req.reply = req.value * 2
        } finally {
            lock.unlock()
        }
        return 0
    } })
    t.start()
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
    hasThree: Bool = xs.contains(3)
    strs: List<String> = xs.map(func(x: Int) -> String { return string(x) })
    joined: String = string.join(strs, ",")
    stackX: Stack<Int> = Stack.new()
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
        .. " contains3=" .. hasThree
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

// `case null` is exempt from case-type checking on nullable switch types.
func nullSwitch(v: String?) -> String {
    switch v {
        case null {
            return "none"
        }
        case "" {
            return "empty"
        }
        default {
            return "value"
        }
    }
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
    sh1: String = hash.sha1("abc")
    sh256: String = hash.sha256("abc")
    sh512: String = hash.sha512("abc")
    rd: Int = int(math.round(3.4))
    pw: Int = int(math.pow(2.0, 10.0))
    return "math=" .. ma .. "," .. mn .. "," .. mx .. "," .. fl .. "," .. ce .. "," .. sq
        .. "," .. rd .. "," .. pw
        .. " path=" .. pj .. "|" .. pb .. "|" .. pd .. "|" .. pe
        .. " b64=" .. enc .. "|" .. dec
        .. " md5=" .. md5v .. " sha1=" .. sh1 .. " sha256=" .. sh256 .. " sha512len=" .. sh512.len()
}

func sectionJson() -> String {
    parsed: Map<String, Any> = json.parse(r#"{"name":"solvik","year":2024,"ok":true}"#)
    name: String = parsed["name"]
    ok: Bool = parsed["ok"]
    return name .. " ok=" .. ok
}

// stringify/parse round trip: only the parsed-back values are printed, so
// key ordering and formatting stay out of the parity contract.
func sectionJsonRoundTrip() -> String {
    doc: Map<String, String> = {"name": "solvik", "version": "1.0"}
    text: String = json.stringify(doc)
    back: Map<String, Any> = json.parse(text)
    return string(back["name"]) .. "/" .. string(back["version"]) .. " len=" .. text.len()
}

// env and random are exercised by property, not by value: seeded draw
// sequences are backend-specific and outside the parity contract.
func sectionEnvRandom() -> String {
    env.set("SOLVIK_EXAMPLE_KEY", "phase15")
    got: String? = env.get("SOLVIK_EXAMPLE_KEY")
    keys: List<String> = env.keys()
    roll: Int = random.int(1, 6)
    inRange: Bool = roll >= 1 && roll <= 6
    pick: String = random.choice(["a", "b"])
    picked: Bool = pick == "a" || pick == "b"
    shuffled: List<Int> = random.shuffle([1, 2, 3])
    sameItems: Bool = shuffled.len() == 3 && shuffled.contains(1) && shuffled.contains(2) && shuffled.contains(3)
    return "env=" .. (got ?? "") .. " inKeys=" .. keys.contains("SOLVIK_EXAMPLE_KEY")
        .. " rollInRange=" .. inRange .. " choiceOk=" .. picked .. " shuffleOk=" .. sameItems
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
    mut jobs: Stack<Job> = Stack.new()
    mut k: Int = 1
    while k <= 3 {
        jobs.push(Job { id: k })
        k = k + 1
    }
    mut results: Map<Int, Int> = {}
    lock: Mutex = Mutex.new()
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
    a: Thread = Thread.new(ThreadDef { body: worker })
    a.start()
    b: Thread = Thread.new(ThreadDef { body: worker })
    b.start()
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
    hasKey: Bool = single.contains("k")
    size: Int = single.len()
    mut pair: String = ""
    for key, value in single {
        pair = key .. "=" .. value
    }
    // Keys come out in canonical sorted order regardless of insert order.
    mut sorted: Map<String, Int> = Map.new()
    sorted["b"] = 2
    sorted["a"] = 1
    mut pair2: String = ""
    for key, value in sorted {
        pair2 = pair2 .. key .. value
    }
    return pair .. " hasKey=" .. hasKey .. " size=" .. size .. " sorted=" .. pair2
}

func testModule() -> Int {
    test.assert(3 > 2, "sanity")
    test.assertTrue(3 > 2)
    test.assertFalse(1 > 2)
    test.assertEq(2 + 2, 4)
    test.assertNe(1, 2)
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
    lock: Mutex = Mutex.new()
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
    a: Thread = Thread.new(ThreadDef { body: worker })
    a.start()
    b: Thread = Thread.new(ThreadDef { body: worker })
    b.start()
    c: Thread = Thread.new(ThreadDef { body: worker })
    c.start()
    ea: Int = a.join()
    eb: Int = b.join()
    ec: Int = c.join()
    done: Bool = a.is_done() && b.is_done() && c.is_done()
    return "total=" .. total .. " exits=" .. ea .. eb .. ec .. " done=" .. done
}

// ----------------------------------------------------------------------------
// 19. External processes: Process.new with standard streams
// ----------------------------------------------------------------------------
// Process.new() builds an unstarted handle; .start() launches argv directly
// stderr to streams. join() waits for exit and returns the child's status;
// buffered output stays readable afterwards. status() polls the cached exit
// code; terminate() is a no-op once exit has been observed.
func sectionProcesses() -> String {
    ok: Process = Process.new(ProcessDef { program: "/bin/sh", args: ["-c", "printf proc-output ; printf proc-err 1>&2"] })
    ok.start()
    ok.stdin.close()
    out: String? = ok.stdout.readLine()
    errLine: String? = ok.stderr.readLine()
    okStatus: Int = ok.join()
    okPoll: Int? = ok.status()
    ok.terminate()
    fail: Process = Process.new(ProcessDef { program: "/bin/false", args: [] })
    fail.start()
    fail.stdin.close()
    badStatus: Int = fail.join()
    return "ok=" .. okStatus .. " stdout=" .. (out ?? "") .. " stderr=" .. (errLine ?? "")
        .. " poll=" .. (okPoll ?? -1) .. " done=" .. ok.is_done() .. " fail=" .. badStatus
}

// ----------------------------------------------------------------------------
// 20. Semaphore: bounding concurrency (Phase 15)
// ----------------------------------------------------------------------------
// At most 2 workers hold the gate at once; the total is independent of
// scheduling because every update is mutex-protected. status() after join()
// returns the cached worker result.
func sectionSemaphore() -> String {
    mut total: Int = 0
    lock: Mutex = Mutex.new()
    gate: Semaphore = Semaphore.new(2)
    worker: Func<Int> = func() -> Int {
        gate.acquire()
        try {
            lock.lock()
            try {
                total = total + 1
            } finally {
                lock.unlock()
            }
        } finally {
            gate.release()
        }
        return 0
    }
    mut i: Int = 0
    mut handles: Stack<Thread> = Stack.new()
    mut last: Thread? = null
    while i < 6 {
        t: Thread = Thread.new(ThreadDef { body: worker })
        t.start()
        handles.push(t)
        last = t
        i = i + 1
    }
    while handles.len() > 0 {
        h: Thread = handles.pop()
        h.join()
    }
    mut polled: Int = -1
    if last != null {
        s: Int? = last.status()
        if s != null {
            polled = s
        }
    }
    nArgs: Int = args().len()
    return "total=" .. total .. " polled=" .. polled .. " args=" .. nArgs
}

// ----------------------------------------------------------------------------
// 21. Function values: named functions, bound methods, identity, nullable
// ----------------------------------------------------------------------------
func doubleIt(x: Int) -> Int {
    return x * 2
}

struct Greeting {
    pub word: String

    pub func greet() -> String {
        return self.word .. "!"
    }
}

// Callable values are ordinary values: they may live inside structs.
struct FnHolder {
    pub f: Func<Int, Int>
}

func sectionFunctionValues() -> String {
    named1: Func<Int, Int> = doubleIt
    named2: Func<Int, Int> = doubleIt
    sameNamed: Bool = named1 == named2
    c1: Func<Int> = func() -> Int { return 1 }
    c2: Func<Int> = func() -> Int { return 1 }
    distinctClosures: Bool = c1 == c2
    maybeFn: Func<Int, Int>? = null
    hasFn: Bool = maybeFn != null
    g: Greeting = Greeting { word: "hi" }
    bound: Func<String> = g.greet
    holder: FnHolder = FnHolder { f: doubleIt }
    return "named=" .. named1(4) .. " same=" .. sameNamed
        .. " distinct=" .. distinctClosures .. " nullFn=" .. hasFn
        .. " bound=" .. bound() .. " held=" .. holder.f(3)
}

// ----------------------------------------------------------------------------
// 22. Traits: multi-constraint (&) and core trait constraints
// ----------------------------------------------------------------------------
trait Labeler {
    func label() -> String
}

// A type parameter may carry several structural constraints at once.
func strCount<T: Stringable & Countable>(v: T) -> Int {
    return v.string().len() + v.len()
}

func cmpVals<T: Comparable>(a: T, b: T) -> Int {
    return a.compare(b)
}

func hashOf<T: Hashable>(v: T) -> Int {
    return v.hash()
}

func eqVals<T: Equatable>(a: T, b: T) -> Bool {
    return a.equals(b)
}

func countOf<C: Collection<Int>>(c: C) -> Int {
    return c.len()
}

func sectionTraits() -> String {
    return "both=" .. strCount("abc")
        .. " cmp=" .. cmpVals(3, 5)
        .. " hash=" .. hashOf(7)
        .. " eq=" .. eqVals("a", "a")
        .. " coll=" .. countOf([1, 2, 3])
}

// ----------------------------------------------------------------------------
// 23. Generics: expected-instantiation seeding, generic methods with
//     explicit type arguments, recursive struct types
// ----------------------------------------------------------------------------
struct Cell<T> {
    pub value: T

    pub func map<R>(f: Func<T, R>) -> R {
        return f(value)
    }
}

// Recursive struct: fields may recurse only through a nullable type.
struct Link<T> {
    pub value: T
    pub next: Link<T>?
}

func sectionGenericsExtra() -> String {
    // The declared target seeds the instantiation, so the field may be null.
    nullableBox: Box<Int?> = Box { value: null }
    seeded: Bool = nullableBox.value == null
    cell: Cell<Int> = Cell { value: 21 }
    mapped: String = cell.map<String>(func(x: Int) -> String { return "x" .. x })
    head: Link<Int> = Link { value: 1, next: Link { value: 2, next: null } }
    second: Int = head.next.value
    return "seeded=" .. seeded .. " map=" .. mapped .. " second=" .. second
}

// ----------------------------------------------------------------------------
// 24. Pattern matching: bare same-enum case names and literal payloads
// ----------------------------------------------------------------------------
enum Traffic {
    Green
    Yellow(Int)
    Red(String)
}

func trafficLight(t: Traffic) -> String {
    switch t {
        case Traffic.Green {
            return "go"
        }
        // Literal payload pattern: matches only Yellow(2).
        case Yellow(2) {
            return "brief"
        }
        case Red(reason) {
            return "stop:" .. reason
        }
        default {
            return "slow"
        }
    }
}

// ----------------------------------------------------------------------------
// 25. Construction: Exception.new, List.new, Map.new
// ----------------------------------------------------------------------------
func sectionConstruction() -> String {
    exc: Exception = Exception.new("crafted")
    emptyList: List<Int> = List.new()
    emptyMap: Map<String, Int> = Map.new()
    return "msg=" .. exc.message .. " list=" .. emptyList.len() .. " map=" .. emptyMap.len()
}

// ----------------------------------------------------------------------------
// 26. Cross-package types via use file:: pub struct/enum/trait, qualified
//     literals and cases, bare same-enum patterns, trait constraints
// ----------------------------------------------------------------------------
func gradeLocal(g: tour.Grade) -> String {
    switch g {
        case tour.Grade.Low {
            return "low"
        }
        case tour.Grade.Mid {
            return "mid"
        }
        default {
            return "high"
        }
    }
}

func measureViaTrait<M: tour.Measurable>(m: M) -> Int {
    return m.measure()
}

func sectionCrossPackage() -> String {
    w: tour.Widget = tour.Widget { id: 7 }
    g: tour.Grade = tour.Grade.High
    v: tour.Verdict<Int> = tour.Verdict.Pass(3)
    vf: tour.Verdict<Int> = tour.Verdict.Fail("nope")
    return w.label() .. " " .. tour.gradeWord(g) .. " " .. gradeLocal(g)
        .. " " .. tour.verdictText(v) .. "," .. tour.verdictText(vf)
        .. " meas=" .. measureViaTrait(w)
}

// ----------------------------------------------------------------------------
// 27. Standard library extras: math trig/constants, secrets, random
//     (property-only), time.now/sleep, path.abs/exists
// ----------------------------------------------------------------------------
func sectionStdlibExtra() -> String {
    s0: Float = math.sin(0.0)
    c0: Float = math.cos(0.0)
    t0: Float = math.tan(0.0)
    piBig: Bool = math.PI > 3.0
    eBig: Bool = math.E > 2.0
    tok: String = secrets.token(8)
    hexv: String = secrets.hex(4)
    r1: Int = random.range(5)
    r1ok: Bool = r1 >= 0 && r1 < 5
    u: Float = random.uniform(1.0, 2.0)
    uok: Bool = u >= 1.0 && u < 2.0
    rf: Float = random.float()
    rfok: Bool = rf >= 0.0 && rf < 1.0
    samp: List<Int> = random.sample([10, 20, 30, 40], 2)
    sampOk: Bool = samp.len() == 2
    t1: Int = time.now()
    time.sleep(5)
    t2: Int = time.now()
    advanced: Bool = t2 >= t1
    absP: String = path.abs("a/b")
    absBase: String = path.basename(absP)
    pExists: Bool = path.exists("example.sol")
    return "sin0=" .. s0 .. " cos0=" .. c0 .. " tan0=" .. t0
        .. " pi=" .. piBig .. " e=" .. eBig
        .. " tokNonEmpty=" .. (tok.len() > 0) .. " hexLen=" .. hexv.len()
        .. " rangeOk=" .. r1ok .. " uniformOk=" .. uok .. " floatOk=" .. rfok
        .. " sampleOk=" .. sampOk
        .. " timeAdv=" .. advanced
        .. " absBase=" .. absBase .. " pathExists=" .. pExists
}

// ----------------------------------------------------------------------------
// 28. File round trip: tempDir/write/read/append/size/rename/mkdir/delete
// ----------------------------------------------------------------------------
func sectionFileRoundTrip() -> String {
    dir: String = file.tempDir("solvik-ex-")
    p1: String = dir .. "/rt.txt"
    file.write(p1, "hello")
    readBack: String = file.read(p1)
    file.append(p1, "!")
    grown: Int = file.size(p1)
    p2: String = dir .. "/rt2.txt"
    file.rename(p1, p2)
    moved: Bool = !file.exists(p1) && file.exists(p2)
    sub: String = dir .. "/sub"
    file.mkdir(sub)
    isSub: Bool = file.isDir(sub)
    file.delete(p2)
    gone: Bool = !file.exists(p2)
    tmp: String = file.temp("solvik-tmp-")
    file.remove(tmp)
    return "read=" .. readBack .. " size=" .. grown .. " moved=" .. moved
        .. " dir=" .. isSub .. " gone=" .. gone
}

// ----------------------------------------------------------------------------
// 29. http client: exercised through a guaranteed-refused loopback address.
//     The caught-exception tag is deterministic offline; if a listener ever
//     bound 127.0.0.1:1 the tag would report the status instead.
// ----------------------------------------------------------------------------
func sectionHttp() -> String {
    mut tag: String = "none"
    try {
        res: Any = http.request("GET", "http://127.0.0.1:1/nope", null, {"X-Tour": "1"})
        tag = "reached:" .. string(res["status"])
    } catch (e: Exception) {
        tag = "caught:" .. typeOf(e)
    }
    mut postTag: String = "none"
    try {
        http.post("http://127.0.0.1:1/nope", "body")
        postTag = "reached"
    } catch (e2: Exception) {
        postTag = "caught:" .. typeOf(e2)
    }
    mut getTag: String = "none"
    try {
        http.get("http://127.0.0.1:1/nope")
        getTag = "reached"
    } catch (e3: Exception) {
        getTag = "caught:" .. typeOf(e3)
    }
    return tag .. " " .. postTag .. " " .. getTag
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
    println("4-break=" .. findFirst([1, 2, 9, 4], 9) .. "," .. firstAbove([1, 3, 9, 4], 5))
    println("4-floatswitch=" .. floatWord(1.0) .. "," .. floatWord(2.5))
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
    println("5-void=" .. runVoid())

    println("6-generic=" .. identity(42) .. "," .. identity("s"))
    println("6-box=" .. Box<Int> { value: 7 }.get())
    println("6-pick=" .. pick(1, 2, true) .. "," .. pick("a", "b", false))
    println("6-twice=" .. applyTwice(2, func(x: Int) -> Int { return x * 3 }))

    mut p: Point = Point { x: 3, y: 4 }
    p.moveBy(1, 2)
    println("7-point=" .. p.describe())
    o: Point = Point.origin()
    println("7-origin=" .. o.describe())
    mut c: Counter = Counter { value: 0, label: "n" }
    c.increment()
    c.increment()
    println("7-counter=" .. c.summary())
    e: Empty = Empty {}
    println("7-empty=ok")
    println("7-equality=" .. (Point { x: 1, y: 2 } == Point { x: 1, y: 2 }))

    println("8-color=" .. describeColor(Color.Blue) .. " int=" .. int(Color.Green))
    println("8-levels=" .. int(Level.Low) .. "," .. int(Level.High))
    println("8-area=" .. shapeArea(Shape.Rect(3, 4)) .. "," .. shapeArea(Shape.Circle(2)) .. "," .. shapeArea(Shape.Group(Shape.Rect(1, 2))))
    println("8-render=" .. string(Shape.Rect(3, 4)))
    println("8-expr=" .. eval(Expr.Add(Expr.Num(2), Expr.Num(3))) .. "," .. eval(Expr.Add(Expr.Num(1), Expr.Add(Expr.Num(1), Expr.Num(1)))) .. "," .. isGroupOfCircles(Shape.Group(Shape.Circle(1))))
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
    println("14-nullswitch=" .. nullSwitch(null) .. "," .. nullSwitch("") .. "," .. nullSwitch("x"))
    println("14-any=" .. anyDemo())
    println("15-term=" .. sectionTermination())
    println("12-stdlib=" .. sectionStdlib())
    println("12-json=" .. sectionJson())
    println("12-jsonrt=" .. sectionJsonRoundTrip())
    println("12-envrand=" .. sectionEnvRandom())
    println("12-time=" .. sectionTime())
    println("12-file=" .. sectionFile())
    println("17-mapiter=" .. mapIteration())
    println("17-test=" .. testModule())
    println("17-explicit=" .. identity<Int>(42) .. "," .. Box<Box<Int>> { value: Box<Int> { value: 6 } }.value.value)
    println("17-shared=" .. squarePool())

    println("18-thread=" .. workerPool())
    println("19-proc=" .. sectionProcesses())
    println("20-semaphore=" .. sectionSemaphore())

    println("21-fnvals=" .. sectionFunctionValues())
    println("22-traits=" .. sectionTraits())
    println("23-generics=" .. sectionGenericsExtra())
    println("24-pattern=" .. trafficLight(Traffic.Green) .. "," .. trafficLight(Traffic.Yellow(2)) .. "," .. trafficLight(Traffic.Yellow(9)) .. "," .. trafficLight(Traffic.Red("rail")))
    println("25-construct=" .. sectionConstruction())
    println("26-xpkg=" .. sectionCrossPackage())
    println("27-stdlib2=" .. sectionStdlibExtra())
    println("28-file2=" .. sectionFileRoundTrip())
    println("29-http=" .. sectionHttp())


    return 0
}
