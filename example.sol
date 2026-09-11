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

// ----------------------------------------------------------------------------
// 1.  Primitives, conversions, nullability
// ----------------------------------------------------------------------------

class Prims {

    public static demo(): Void {
        let mutable count: Long = 42
        count += 1
        stdout.println(count)

        let pi: Double = 3.14159
        stdout.println(pi * 2.0)

        let ok: Bool = true && false || true
        stdout.println(ok)

        let ch: Char = 'A'
        stdout.println(ch)

        // Conversions go through <Type>.from(...).
        stdout.println(Long.from("7"))
        stdout.println(Double.from(3))
        stdout.println(String.from(99))
        stdout.println(Bool.from(0))

        // Nullability and coalesce.
        let n: Long? = null
        let m: Long? = 5
        stdout.println(n == null)
        stdout.println(n ?? 10)
        stdout.println(m ?? 10)
    }
}

// ----------------------------------------------------------------------------
// 2.  Strings and regex
// ----------------------------------------------------------------------------

class Strs {

    public static demo(): Void {
        let s: String = "hello"
        stdout.println(s.length())
        stdout.println("foo" .. "bar")
        stdout.println(s.substring(1, 3))
        stdout.println(s.contains("ell"))
        stdout.println(s.toUpperCase())
        let parts: List<String> = s.split("l")
        stdout.println(parts.size())
        stdout.println(s.replace("l", "L"))

        let r: Regex = Regex.new("[a-z]+")
        stdout.println(r.matches("abc"))
        stdout.println(r.find("123 abc 456"))
        let all: List<String> = r.all("one two three")
        stdout.println(all.size())
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
            stdout.println("big")
        } else {
            stdout.println("small")
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
            stdout.print(i .. " ")
        }
        stdout.println("")

        // range for-in
        let mutable total: Long = 0
        for n in 1..6 {
            total += n
        }
        stdout.println(total)

        // for-in over a list
        let xs: List<Long> = [10, 20, 30]
        for v in xs {
            stdout.print(v .. " ")
        }
        stdout.println("")
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
        stdout.println(e.name())
        stdout.println(e.title())
        // External field access is a compile error: stdout.println(e.person)
        let p: Named = e
        stdout.println(p.name())

        // Multiple delegates: each interface is forwarded to its own field.
        let r: Registered = Registered.new("Grace", 1001)
        stdout.println(r.name())
        stdout.println(r.id())
        // The private delegate fields remain inaccessible:
        //   stdout.println(r.person)
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
        stdout.println("static block total=" .. String.from(total))
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
        stdout.println("static shared " .. a.current())
        stdout.println("static limit " .. b.current())
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
        stdout.println(g.greeting())
        stdout.println(g.farewell())
        let b: Bot = Bot.new()
        stdout.println(b.farewell())
        let l: LoggingBot = LoggingBot.new()
        stdout.println(l.greeting())
        stdout.println(l.farewell())
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

class Gen {

    public static demo(): Void {
        let b: Box<Long> = Box<Long>.new(41)
        b.set(42)
        stdout.println(b.get())
        let s: Box<String> = Box<String>.new("hi")
        stdout.println(s.get())
        let p: Pair<Long, String> = Pair<Long, String>.new(7, "seven")
        let q: Pair<String, Long> = p.swap()
        stdout.println(q.first() .. "=" .. q.second())
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

class Enums {

    public static demo(): Void {
        let c: Color = Color.red
        let d: Color = Color.blue(255)
        match c {
            Color.red => stdout.println("red")
            Color.green => stdout.println("green")
            Color.blue(r) => stdout.println("blue " .. r)
            _ => stdout.println("?")
        }
        match d {
            Color.blue(r) => stdout.println("got " .. r)
            _ => stdout.println("not blue")
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
            stdout.println("caught " .. e)
        }
        try {
            stdout.println("work")
        } finally {
            stdout.println("cleaned")
        }
        // An exception propagates through a finally without catch.
        try {
            try {
                throw "deep"
            } finally {
                stdout.println("inner finally")
            }
        } catch (e) {
            stdout.println("outer caught " .. e)
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
        stdout.println(x.get(0))
        stdout.println(x.contains(3))
        stdout.println(x.join(","))

        let m: Map<String, Long> = { "a": 1, "b": 2 }
        m.put("c", 3)
        stdout.println(m.size())
        stdout.println(m.get("a"))
        for k in m {
            stdout.print(k .. "=" .. m.get(k) .. " ")
        }
        stdout.println("")

        let st: Stack<Long> = Stack<Long>.new()
        st.push(1)
        st.push(2)
        stdout.println(st.pop())
        stdout.println(st.peek())

        let u: Set<Long> = Set<Long>.new()
        u.add(5)
        u.add(6)
        u.add(5)
        stdout.println(u.size())
        stdout.println(u.contains(5))
        u.remove(5)
        stdout.println(u.contains(5))
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
        stdout.println("worker up to " .. self.target)
    }
}

class Conc {

    public static demo(): Void {
        let t: Thread = Thread.new(Counter.new(3))
        t.start()
        t.join()
        stdout.println("joined")

        let mu: Mutex = Mutex.new()
        mu.lock()
        mu.unlock()
        stdout.println("mutex ok")
    }
}

// ----------------------------------------------------------------------------
// 11. Standard library: deterministic digests, base64, json, math
// ----------------------------------------------------------------------------

class Stdlib {

    public static demo(): Void {
        stdout.println(Math.sqrt(16.0))
        stdout.println(Math.pow(2.0, 8.0))
        stdout.println(Base64.encode("solvik"))
        stdout.println(Base64.decode(Base64.encode("round trip")))
        stdout.println(Hash.sha256("abc"))
        let m: Map<String, Object> = { "k": 1 }
        stdout.println(Json.stringify(m))
        let now: Long = Time.now()
        stdout.println(now > 0)
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
        stdout.println(Type.of(a))
        stdout.println(Type.of(b))
        stdout.println(Type.isType(o, "Long"))
        stdout.println(Type.isType(b, "String"))
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
        stdout.println(x)   // 2

        // Type-changing shadow.
        let y: Long = 10
        let y: String = "ten"
        stdout.println(y)   // ten

        // Block scoping: a binding declared inside a block is restored after
        // it; loop/catch locals do not leak past their body.
        let z: Long = 100
        if true {
            let z: Long = 200
            stdout.println(z)   // 200
        }
        stdout.println(z)   // 100 (outer z restored)

        let w: Long = 5
        for w in [1, 2] {
            stdout.print(w .. " ")   // 1 2
        }
        stdout.println("")
        stdout.println(w)   // 5 (outer w restored after the loop)
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
            stdout.println("allman yes")
        }
        else
        {
            stdout.println("allman no")
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
// Entry point
// ----------------------------------------------------------------------------

class Main {

    public static run(args: String...): Long {
        Prims.demo()
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
        stdout.println(AllmanClass.answer())
        return 0
    }
}
