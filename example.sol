// ============================================================================
//  example.sol -- Solvik Language Example
//
//  A complete, deterministic tour of the Solvik language on the Rust
//  bytecode VM. Every language construct is exercised here.
//
//  Run:  solvik example.sol
// ============================================================================

/* Nested block comments are legal and may nest: /* inner */ outer */

package example

// Dependency metadata. `use file:` declarations are parsed and preserved as
// package metadata; the compiler is single-file, so nothing is loaded.
// (`use url:` exists in the grammar but is deliberately not exercised here.)
use file:vendor.stringkit
use file:vendor.textkit as tk

// ----------------------------------------------------------------------------
// 1.  Primitives, conversions, nullability
// ----------------------------------------------------------------------------

class Prims {

    public static demo(): Void {
        let mutable count: Long = 42
        count += 1
        System.out().println(count)

        let pi: Double = 3.14159
        System.out().println(pi * 2.0)

        let ok: Bool = true && false || true
        System.out().println(ok)

        let ch: Char = 'A'
        System.out().println(ch)

        // Conversions go through <Type>.from(...).
        System.out().println(Long.from("7"))
        System.out().println(Double.from(3))
        System.out().println(String.from(99))
        System.out().println(Bool.from(0))
        System.out().println(Byte.from(7))
        System.out().println(Char.from('x'))

        // Nullability and coalesce.
        let n: Long? = null
        let m: Long? = 5
        System.out().println(n == null)
        System.out().println(n ?? 10)
        System.out().println(m ?? 10)

        // Null narrowing: inside a != null branch the binding is seen as
        // non-nullable, so arithmetic is allowed without a coalesce.
        if m != null {
            System.out().println("narrowed " .. (m + 1))
        }
    }
}

// ----------------------------------------------------------------------------
// 1b. Literals: numeric bases, separators, escapes, raw strings
// ----------------------------------------------------------------------------

class Lit {

    public static demo(): Void {
        // Integer literals: decimal, hex, octal, binary; _ digit separators.
        let dec: Long = 1234567
        let hex: Long = 0xff
        let oct: Long = 0o17
        let bin: Long = 0b101
        let sep: Long = 1_000_000
        System.out().println(dec == 1234567)
        System.out().println(hex == 255)
        System.out().println(oct == 15)
        System.out().println(bin == 5)
        System.out().println(sep == 1000000)

        // Float literals: fraction and exponent forms.
        let f1: Double = 2.5
        let f2: Double = 1e3
        let f3: Double = -2.5e-1
        System.out().println(f1 + f2 + f3)

        // String escapes: \t \xHH \uHHHH \u{...}.
        System.out().println("tab\there")
        System.out().println("\x41\u0042\u{43}")
        // Remaining escapes, verified by equality against unicode forms
        // (printing raw NUL/carriage returns would be noisy).
        System.out().println("\r" == "\u000D")
        System.out().println("\0" == "\u0000")
        System.out().println("\\n" == "\u005cn")
        System.out().println("\"" == "\u0022")
        System.out().println("'" == "\u0027")
        System.out().println("\U0001F600" == "\u{1F600}")
        // Raw strings disable escaping; r#"..."# allows embedded quotes;
        // r##"..."## raises the hash level past embedded # sequences.
        System.out().println(r"back\slash \n stays raw")
        System.out().println(r#"quote " inside"#)
        System.out().println(r##"one # two ## three"##)

        // Char escapes.
        let nl: Char = '\n'
        System.out().println(nl == '\n')
    }
}

// ----------------------------------------------------------------------------
// 1c. Operators, semicolons, named arguments, default parameters,
//     private methods
// ----------------------------------------------------------------------------

class Point {

    x: Long
    y: Long

    // Default parameter values make trailing arguments optional.
    public static new(x: Long = 0, y: Long = 0): Self {
        return Self { x: x, y: y, }
    }

    // Omitting `public` makes a method private to the class.
    sq(v: Long): Long {
        return v * v
    }

    public dist(): Long {
        return self.sq(self.x) + self.sq(self.y)
    }
}

class Ops {

    public static demo(): Void {
        // Unary minus and logical not.
        let neg: Long = -42
        let flag: Bool = true
        System.out().println(neg * -1)
        System.out().println(!flag)

        // Compound assignments: -= *= /= %= ..=
        let mutable a: Long = 10
        a -= 4
        a *= 3
        a /= 2
        a %= 7
        System.out().println(a)   // ((10-4)*3)/2 % 7 == 2

        let mutable s: String = "ab"
        s ..= "cd"
        System.out().println(s)   // abcd

        // Semicolons remain accepted statement separators.
        let one: Long = 1; let two: Long = 2
        System.out().println(one + two)

        // Calls: positional, named, mixed, defaulted, out-of-order named.
        let p1: Point = Point.new(3, 4)
        let p2: Point = Point.new(x: 6, y: 8)
        let p3: Point = Point.new(1, y: 2)
        let p4: Point = Point.new()
        let p5: Point = Point.new(y: 5, x: 12)
        System.out().println(p1.dist())
        System.out().println(p2.dist())
        System.out().println(p3.dist())
        System.out().println(p4.dist())
        System.out().println(p5.dist())
    }
}

// ----------------------------------------------------------------------------
// 2.  Strings and regex
// ----------------------------------------------------------------------------

class Strs {

    public static demo(): Void {
        let s: String = "hello"
        System.out().println(s.length())
        System.out().println("foo" .. "bar")
        System.out().println(s.substring(1, 3))
        System.out().println(s.contains("ell"))
        System.out().println(s.startsWith("he"))
        System.out().println(s.endsWith("lo"))
        System.out().println(s.indexOf("l"))
        System.out().println(s.charAt(1))
        System.out().println("  pad  ".trim())
        System.out().println(s.toUpperCase())
        System.out().println(s.toLowerCase())
        let parts: List<String> = s.split("l")
        System.out().println(parts.size())
        System.out().println(s.replace("l", "L"))

        let r: Regex = Regex.new("[a-z]+")
        System.out().println(r.matches("abc"))
        System.out().println(r.find("123 abc 456"))
        let all: List<String> = r.all("one two three")
        System.out().println(all.size())
    }
}

// ----------------------------------------------------------------------------
// 3.  Control flow
// ----------------------------------------------------------------------------

class Flow {

    public static demo(): Void {
        // if / else
        let x: Long = 7
        if x > 5 {
            System.out().println("big")
        } else {
            System.out().println("small")
        }

        // while with break / continue
        let mutable i: Long = 0
        while true {
            i += 1
            if i % 2 == 0 {
                continue
            }
            if i > 6 {
                break
            }
            System.out().print(i .. " ")
        }
        System.out().println("")

        // range for-in
        let mutable total: Long = 0
        for n in 1..6 {
            total += n
        }
        System.out().println(total)

        // for-in over a list
        let xs: List<Long> = [10, 20, 30]
        for v in xs {
            System.out().print(v .. " ")
        }
        System.out().println("")

        // else-if chain
        let grade: Long = 87
        if grade >= 90 {
            System.out().println("A")
        } else if grade >= 80 {
            System.out().println("B")
        } else if grade >= 70 {
            System.out().println("C")
        } else {
            System.out().println("F")
        }

        // switch: multi-value cases, default, case-body scoping
        let day: Long = 6
        switch day {
            case 1, 2, 3, 4, 5: {
                System.out().println("workday")
            }
            case 6, 7: {
                System.out().println("weekend")
            }
            default: {
                System.out().println("invalid day")
            }
        }

        // Standalone scope blocks create a fresh name scope; break resolves
        // through them to the nearest enclosing loop.
        {
            let inner: Long = 1
            System.out().println("scoped " .. inner)
        }
        let mutable k: Long = 0
        while true {
            {
                k += 1
                if k >= 3 {
                    break
                }
            }
        }
        System.out().println("scope loop " .. k)
    }
}

// ----------------------------------------------------------------------------
// 4.  Classes: private fields, methods, and composition
// ----------------------------------------------------------------------------

interface Named {

    name(): String
}

interface Identified {

    id(): Long
}

// Fields are always private; methods are the external API.
class Person implements Named {

    nameValue: String

    public static new(name: String): Self {
        return Self { nameValue: name, }
    }

    public name(): String {
        return self.nameValue
    }
}

// Composition replaces inheritance: Employee is not a Person, but it exposes
// the Named contract by delegating to a private composed field.
class Employee implements Named {

    person: Person
    titleValue: String

    delegate Named to person

    public static new(name: String, title: String): Self {
        return Self {
            person: Person.new(name),
            titleValue: title,
        }
    }

    public title(): String {
        return self.titleValue
    }
}

class Cls {

    public static demo(): Void {
        let e: Employee = Employee.new("Ada", "Engineer")
        System.out().println(e.name())
        System.out().println(e.title())
        // External field access is a compile error: System.out().println(e.person)
        let p: Named = e
        System.out().println(p.name())

        // Multiple delegates: each interface is forwarded to its own field.
        let r: Registered = Registered.new("Grace", 1001)
        System.out().println(r.name())
        System.out().println(r.id())
        // The private delegate fields remain inaccessible:
        //   System.out().println(r.person)
    }
}

class Badge implements Identified {

    idValue: Long

    public static new(id: Long): Self {
        return Self { idValue: id, }
    }

    public id(): Long {
        return self.idValue
    }
}

class Registered implements Named, Identified {

    person: Person
    badge: Badge

    delegate Named to person
    delegate Identified to badge

    public static new(name: String, id: Long): Self {
        return Self {
            person: Person.new(name),
            badge: Badge.new(id),
        }
    }
}

// ----------------------------------------------------------------------------
// 4b. Static fields and static blocks: class-level state shared by all
//     instances
// ----------------------------------------------------------------------------

// Static fields are private to their declaring class, initialized exactly
// once before Main.run (in declaration order), and accessed only through
// type-qualified names: Ticker.total and Self.total are equivalent here.
// A class may declare at most one static block; it runs once, after every
// static field initializer of the class. Inside the block, static members
// of the declaring class resolve by bare name.
class Ticker {

    static count: Long = 0
    static mutable total: Long = 0
    static limit: Long = 10

    static {
        // Field initializers have already run: total == 0, limit == 10.
        total += 5
        System.out().println("static block total=" .. String.from(total))
    }

    public static new(): Self {
        return Self {}
    }

    public static tick(): Long {
        Self.total += 1
        if Self.total > Ticker.limit {
            Ticker.total = Ticker.limit
        }
        return Self.total
    }

    public current(): Long {
        return Ticker.total
    }
}

class Statics {

    public static demo(): Void {
        let a: Ticker = Ticker.new()
        let b: Ticker = Ticker.new()
        Ticker.tick()
        Ticker.tick()
        // Both instances observe the same shared slot (5 from the static
        // block plus two ticks).
        System.out().println("static shared " .. a.current())
        System.out().println("static limit " .. b.current())
    }
}

// ----------------------------------------------------------------------------
// 5.  Interfaces: implements, default methods, delegation
// ----------------------------------------------------------------------------

interface Greetable {

    greeting(): String

    farewell(): String {
        return "bye from " .. greeting()
    }
}

// Interfaces may extend other interfaces: contract refinement. A default
// method may call sibling requirements; the call dispatches through the
// receiver to the implementing class's effective implementation.
interface Labeled extends Named {

    label(): String

    describe(): String {
        return label() .. ":" .. name()
    }
}

// Diamond base: a default method refined independently by two children.
interface BaseKind {

    kind(): String {
        return "base"
    }
}

interface Left extends BaseKind {

    kind(): String {
        return "left"
    }
}

interface Right extends BaseKind {

    kind(): String {
        return "right"
    }
}

class Tag implements Labeled {

    nameValue: String
    tagValue: String

    public static new(name: String, tag: String): Self {
        return Self { nameValue: name, tagValue: tag, }
    }

    public name(): String {
        return self.nameValue
    }

    public label(): String {
        return self.tagValue
    }
}

// Generic interfaces: type arguments at the conformance site.
interface Boxed<T> {

    value(): T

    dup(): T {
        return value()
    }
}

class SevenBox implements Boxed<Long> {

    public static new(): Self {
        return Self {}
    }

    public value(): Long {
        return 7
    }
}

// Diamond inheritance: two parents refine the same default differently.
class OnlyLeft implements Left {

    public static new(): Self {
        return Self {}
    }
}

// When two defaults would compete, the class must declare the method
// explicitly; the explicit implementation wins.
class BothSides implements Left, Right {

    public static new(): Self {
        return Self {}
    }

    public kind(): String {
        return "both"
    }
}

class Bot implements Greetable {

    public static new(): Self {
        return Self {}
    }

    public greeting(): String {
        return "bot"
    }
}

class PoliteBot implements Greetable {

    public static new(): Self {
        return Self {}
    }

    public greeting(): String {
        return "polite bot"
    }
}

// An explicit class method beats a delegated implementation for that method.
class LoggingBot implements Greetable {

    inner: Bot

    delegate Greetable to inner

    public static new(): Self {
        return Self { inner: Bot.new(), }
    }

    public farewell(): String {
        return "logged: " .. self.inner.greeting()
    }
}

class Ifaces {

    public static demo(): Void {
        let g: Greetable = PoliteBot.new()
        System.out().println(g.greeting())
        System.out().println(g.farewell())
        let b: Bot = Bot.new()
        System.out().println(b.farewell())
        let l: LoggingBot = LoggingBot.new()
        System.out().println(l.greeting())
        System.out().println(l.farewell())

        // Interface extension: Tag satisfies both Labeled and Named.
        let t: Labeled = Tag.new("ada", "core")
        System.out().println(t.describe())
        let tn: Named = t
        System.out().println(tn.name())

        // Generic interface with a default method.
        let sb: Boxed<Long> = SevenBox.new()
        System.out().println(sb.dup())

        // Diamond defaults: the most-specific unambiguous default wins...
        let ol: Left = OnlyLeft.new()
        System.out().println(ol.kind())
        // ...and an explicit class method beats competing defaults.
        let bs: BothSides = BothSides.new()
        System.out().println(bs.kind())
    }
}

// ----------------------------------------------------------------------------
// 6.  Generics
// ----------------------------------------------------------------------------

class Box<T> {

    mutable value: T

    public static new(value: T): Self {
        return Self { value: value, }
    }

    public get(): T {
        return self.value
    }

    public set(v: T): Void {
        self.value = v
    }
}

class Pair<A, B> {

    firstValue: A
    secondValue: B

    public static new(first: A, second: B): Self {
        return Self { firstValue: first, secondValue: second, }
    }

    public first(): A {
        return self.firstValue
    }

    public second(): B {
        return self.secondValue
    }

    public swap(): Pair<B, A> {
        return Pair<B, A>.new(self.secondValue, self.firstValue)
    }
}

class Ids {

    // Generic methods: type arguments are inferred at the call site.
    public static identity<U>(v: U): U {
        return v
    }

    // Type parameters may carry an interface constraint; the constraint is
    // checked at the instantiation site, while the body sees the type
    // parameter as Object (type erasure).
    public static pick<T: Named>(a: T): T {
        return a
    }
}

class Gen {

    public static demo(): Void {
        let b: Box<Long> = Box<Long>.new(41)
        b.set(42)
        System.out().println(b.get())
        let s: Box<String> = Box<String>.new("hi")
        System.out().println(s.get())
        let p: Pair<Long, String> = Pair<Long, String>.new(7, "seven")
        let q: Pair<String, Long> = p.swap()
        System.out().println(q.first() .. "=" .. q.second())

        // Generic methods with inferred type arguments.
        let r: Long = Ids.identity(99)
        System.out().println(r)
        let w: String = Ids.identity("kept")
        System.out().println(w)

        // Constrained generic method: Person satisfies the Named bound.
        let ada: Person = Person.new("Ada Lovelace")
        let top: Named = Ids.pick(ada)
        System.out().println(top.name())
    }
}

// ----------------------------------------------------------------------------
// 7.  Enums and pattern matching
// ----------------------------------------------------------------------------

enum Color {

    red
    green
    blue(Long)
}

// Generic enum: type arguments are written at the use site.
enum Verdict<T> {

    pass(T)
    fail(String)
}

class Enums {

    public static demo(): Void {
        let c: Color = Color.red
        let d: Color = Color.blue(255)
        match c {
            Color.red => System.out().println("red")
            Color.green => System.out().println("green")
            Color.blue(r) => System.out().println("blue " .. r)
            _ => System.out().println("?")
        }
        match d {
            Color.blue(r) => System.out().println("got " .. r)
            _ => System.out().println("not blue")
        }

        // Enum values compare by variant identity (and payload equality).
        System.out().println(c == Color.red)
        System.out().println(c != d)

        // Literal patterns on a primitive subject.
        let code: Long = 2
        match code {
            1 => System.out().println("one")
            2 => System.out().println("two")
            _ => System.out().println("many")
        }

        // Nested list pattern with bindings.
        let pair: List<Long> = [3, 4]
        match pair {
            [a, b] => System.out().println("pair " .. a .. "+" .. b)
            _ => System.out().println("not a pair")
        }

        // Literal patterns: float, bool, char, string.
        let ratio: Double = 1.5
        match ratio {
            1.5 => System.out().println("ratio")
            _ => System.out().println("other ratio")
        }
        let flag: Bool = true
        match flag {
            true => System.out().println("flag set")
            false => System.out().println("flag clear")
        }
        let zed: Char = 'z'
        match zed {
            'z' => System.out().println("zed")
            _ => System.out().println("not zed")
        }
        let word: String = "hi"
        match word {
            "hi" => System.out().println("greeting")
            _ => System.out().println("unknown")
        }

        // Generic enum variants carry the instantiated payload type.
        let v: Verdict<Long> = Verdict<Long>.pass(1)
        match v {
            Verdict.pass(score) => System.out().println("pass " .. score)
            Verdict.fail(reason) => System.out().println("fail " .. reason)
        }
    }
}

// ----------------------------------------------------------------------------
// 8.  Exceptions
// ----------------------------------------------------------------------------

class Excs {

    public static demo(): Void {
        try {
            throw "boom"
        } catch (e) {
            System.out().println("caught " .. e)
        }
        try {
            System.out().println("work")
        } finally {
            System.out().println("cleaned")
        }
        // An exception propagates through a finally without catch.
        try {
            try {
                throw "deep"
            } finally {
                System.out().println("inner finally")
            }
        } catch (e) {
            System.out().println("outer caught " .. e)
        }
    }
}

// ----------------------------------------------------------------------------
// 9.  Collections
// ----------------------------------------------------------------------------

class Colls {

    public static demo(): Void {
        let x: List<Long> = [1, 2, 3]
        x.add(4)
        x.set(0, 10)
        System.out().println(x.get(0))
        System.out().println(x.contains(3))
        System.out().println(x.join(","))

        let m: Map<String, Long> = { "a": 1, "b": 2 }
        m.put("c", 3)
        System.out().println(m.size())
        System.out().println(m.get("a"))
        for k in m {
            System.out().print(k .. "=" .. m.get(k) .. " ")
        }
        System.out().println("")

        let st: Stack<Long> = Stack<Long>.new()
        st.push(1)
        st.push(2)
        System.out().println(st.pop())
        System.out().println(st.peek())

        let u: Set<Long> = Set<Long>.new()
        u.add(5)
        u.add(6)
        u.add(5)
        System.out().println(u.size())
        System.out().println(u.contains(5))
        u.remove(5)
        System.out().println(u.contains(5))
        u.clear()
        System.out().println(u.isEmpty())

        // Remaining List built-ins.
        let l2: List<Long> = List<Long>.new()
        System.out().println(l2.isEmpty())
        l2.add(3)
        l2.add(1)
        l2.add(2)
        l2.sort()
        System.out().println(l2.get(0))
        System.out().println(l2.indexOf(2))
        l2.reverse()
        System.out().println(l2.get(0))
        l2.remove(0)
        System.out().println(l2.size())
        l2.clear()
        System.out().println(l2.isEmpty())

        // Remaining Map built-ins.
        let m2: Map<String, Long> = Map<String, Long>.new()
        System.out().println(m2.isEmpty())
        m2.put("x", 1)
        System.out().println(m2.containsKey("x"))
        let ks: List<String> = m2.keys()
        let vs: List<Long> = m2.values()
        System.out().println(ks.size())
        System.out().println(vs.size())
        m2.remove("x")
        System.out().println(m2.isEmpty())
        m2.put("y", 2)
        m2.clear()
        System.out().println(m2.size())

        // Remaining Stack built-ins.
        let st2: Stack<Long> = Stack<Long>.new()
        st2.push(9)
        System.out().println(st2.size())
        System.out().println(st2.isEmpty())
    }
}

// ----------------------------------------------------------------------------
// 10. Concurrency: threads over a shared heap, guarded by a mutex
// ----------------------------------------------------------------------------

class Counter implements Runnable {

    target: Long

    public static new(target: Long): Self {
        return Self { target: target, }
    }

    public run(): Void {
        System.out().println("worker up to " .. self.target)
    }
}

class Conc {

    public static demo(): Void {
        let t: Thread = Thread.new(Counter.new(3))
        t.start()
        t.join()
        System.out().println("joined")

        let mu: Mutex = Mutex.new()
        mu.lock()
        mu.unlock()
        System.out().println("mutex ok")

        let sem: Semaphore = Semaphore.new(2)
        sem.acquire()
        sem.release()
        System.out().println("semaphore ok")
    }
}

// ----------------------------------------------------------------------------
// 11. Standard library: deterministic digests, base64, json, math
// ----------------------------------------------------------------------------

class Stdlib {

    public static demo(): Void {
        System.out().println(Math.sqrt(16.0))
        System.out().println(Math.pow(2.0, 8.0))
        System.out().println(Math.abs(-3.5))
        System.out().println(Math.min(1.5, 2.5))
        System.out().println(Math.max(1.5, 2.5))
        System.out().println(Math.floor(2.7))
        System.out().println(Math.ceil(2.1))
        System.out().println(Math.round(2.5))
        System.out().println(Base64.encode("solvik"))
        System.out().println(Base64.decode(Base64.encode("round trip")))
        System.out().println(Hash.md5("abc"))
        System.out().println(Hash.sha1("abc"))
        System.out().println(Hash.sha256("abc"))
        let m: Map<String, Object> = { "k": 1 }
        let j: String = Json.stringify(m)
        System.out().println(j)
        let parsed: Object = Json.parse(j)
        System.out().println(parsed)
        let now: Long = Time.now()
        System.out().println(now > 0)
        Time.sleep(0)
        Random.seed(42)
        System.out().println(Random.nextLong(1000))
        System.out().println(Random.nextDouble() >= 0.0)
        Test.assert(true, "assert should hold")
        Test.assertEqual(2 + 2, 4)
        let rr: Regex = Regex.new("o+")
        System.out().println(rr.replace("too many loops", "0"))
        System.err().print("err stream ok")

        // File: write/read/exists/delete round trip (self-cleaning).
        let path: String = "example-tmp.txt"
        File.write(path, "round trip")
        System.out().println(File.exists(path))
        System.out().println(File.read(path))
        File.delete(path)
        System.out().println(File.exists(path))

        // Process: run a command, wait, inspect exit code and output.
        let noArgs: List<String> = []
        let p: Process = Process.new("printf done", noArgs)
        p.start()
        p.wait()
        System.out().println(p.exitCode())
        System.out().println(p.stdout().readAll())
    }
}

// ----------------------------------------------------------------------------
// 12. Introspection
// ----------------------------------------------------------------------------

class Introspect {

    public static demo(): Void {
        let a: Long = 5
        let b: String = "text"
        let o: Object = a
        System.out().println(Type.of(a))
        System.out().println(Type.of(b))
        System.out().println(Type.isType(o, "Long"))
        System.out().println(Type.isType(b, "String"))
    }
}

// ----------------------------------------------------------------------------
// 13. Shadowing and block scoping
// ----------------------------------------------------------------------------

class Scope {

    public static demo(): Void {
        // Same-block shadow: the second `let` hides the first for the rest
        // of the block (emits warning W101).
        let x: Long = 1
        let x: Long = 2
        System.out().println(x)   // 2

        // Type-changing shadow.
        let y: Long = 10
        let y: String = "ten"
        System.out().println(y)   // ten

        // Block scoping: a binding declared inside a block is restored after
        // it; loop/catch locals do not leak past their body.
        let z: Long = 100
        if true {
            let z: Long = 200
            System.out().println(z)   // 200
        }
        System.out().println(z)   // 100 (outer z restored)

        let w: Long = 5
        for w in [1, 2] {
            System.out().print(w .. " ")   // 1 2
        }
        System.out().println("")
        System.out().println(w)   // 5 (outer w restored after the loop)
    }
}

// ----------------------------------------------------------------------------
// 14. Brace placement: the opening brace may sit on the line after its
//     header (class, method, if/else, while, for, switch, try/catch/finally,
//     match). Behavior is identical to same-line braces.
// ----------------------------------------------------------------------------

class Allman {

    public static demo(): Void
    {
        let flag: Bool = true
        if flag
        {
            System.out().println("allman yes")
        }
        else
        {
            System.out().println("allman no")
        }
    }
}

class AllmanClass
{

    public static answer(): Long
    {
        return 42
    }
}

// ----------------------------------------------------------------------------
// 1d. Variadic parameters: calls, empty calls, and list spread (...list)
// ----------------------------------------------------------------------------

class Varargs {

    public static sum(values: Long...): Long {
        let mutable total: Long = 0
        for v in values {
            total += v
        }
        return total
    }

    public static demo(): Void {
        System.out().println(Varargs.sum(1, 2, 3))
        System.out().println(Varargs.sum())
        let vs: List<Long> = [4, 5]
        System.out().println(Varargs.sum(...vs))
    }
}

// ----------------------------------------------------------------------------
// Entry point
// ----------------------------------------------------------------------------

class Main {

    public static run(args: String...): Long {
        // The variadic entry-point argument list is an ordinary List.
        System.out().println("args=" .. args.size())
        Prims.demo()
        Lit.demo()
        Ops.demo()
        Varargs.demo()
        Strs.demo()
        Flow.demo()
        Cls.demo()
        Statics.demo()
        Ifaces.demo()
        Gen.demo()
        Enums.demo()
        Excs.demo()
        Colls.demo()
        Conc.demo()
        Stdlib.demo()
        Introspect.demo()
        Scope.demo()
        Allman.demo()
        System.out().println(AllmanClass.answer())
        return 0
    }
}
