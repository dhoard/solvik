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

        // Nullability and coalesce.
        let n: Long? = null
        let m: Long? = 5
        System.out().println(n == null)
        System.out().println(n ?? 10)
        System.out().println(m ?? 10)
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
        System.out().println(s.toUpperCase())
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
        System.out().println(b.get())
        let s: Box<String> = Box<String>.new("hi")
        System.out().println(s.get())
        let p: Pair<Long, String> = Pair<Long, String>.new(7, "seven")
        let q: Pair<String, Long> = p.swap()
        System.out().println(q.first() .. "=" .. q.second())
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
            Color.red => System.out().println("red")
            Color.green => System.out().println("green")
            Color.blue(r) => System.out().println("blue " .. r)
            _ => System.out().println("?")
        }
        match d {
            Color.blue(r) => System.out().println("got " .. r)
            _ => System.out().println("not blue")
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
    }
}

// ----------------------------------------------------------------------------
// 11. Standard library: deterministic digests, base64, json, math
// ----------------------------------------------------------------------------

class Stdlib {

    public static demo(): Void {
        System.out().println(Math.sqrt(16.0))
        System.out().println(Math.pow(2.0, 8.0))
        System.out().println(Base64.encode("solvik"))
        System.out().println(Base64.decode(Base64.encode("round trip")))
        System.out().println(Hash.sha256("abc"))
        let m: Map<String, Object> = { "k": 1 }
        System.out().println(Json.stringify(m))
        let now: Long = Time.now()
        System.out().println(now > 0)
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
        System.out().println(AllmanClass.answer())
        return 0
    }
}
