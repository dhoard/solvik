// ============================================================================
//  example.sol -- Solvik Language Example
//
//  A complete, deterministic tour of the Solvik language on the Rust
//  bytecode VM. Every language construct is exercised here.
//
//  Run:  solvik example.sol
// ============================================================================

/* Nested block comments are legal and may nest: /* inner */ outer */

module example

// ----------------------------------------------------------------------------
// 1.  Primitives, conversions, nullability
// ----------------------------------------------------------------------------

class Prims {

    public static demo(): Void {
        mutable count: Long = 42
        count += 1
        stdout.println(count)

        pi: Double = 3.14159
        stdout.println(pi * 2.0)

        ok: Bool = true && false || true
        stdout.println(ok)

        ch: Char = 'A'
        stdout.println(ch)

        // Conversions go through <Type>.from(...).
        stdout.println(Long.from("7"))
        stdout.println(Double.from(3))
        stdout.println(String.from(99))
        stdout.println(Bool.from(0))

        // Nullability and coalesce.
        n: Long? = null
        m: Long? = 5
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
        s: String = "hello"
        stdout.println(s.length())
        stdout.println("foo" .. "bar")
        stdout.println(s.substring(1, 3))
        stdout.println(s.contains("ell"))
        stdout.println(s.toUpperCase())
        parts: List<String> = s.split("l")
        stdout.println(parts.size())
        stdout.println(s.replace("l", "L"))

        r: Regex = Regex.new("[a-z]+")
        stdout.println(r.matches("abc"))
        stdout.println(r.find("123 abc 456"))
        all: List<String> = r.all("one two three")
        stdout.println(all.size())
    }
}

// ----------------------------------------------------------------------------
// 3.  Control flow
// ----------------------------------------------------------------------------

class Flow {

    public static demo(): Void {
        // if / else
        x: Long = 7
        if x > 5 {
            stdout.println("big")
        } else {
            stdout.println("small")
        }

        // while with break / continue
        mutable i: Long = 0
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
        mutable total: Long = 0
        for n in 1..6 {
            total += n
        }
        stdout.println(total)

        // for-in over a list
        xs: List<Long> = [10, 20, 30]
        for v in xs {
            stdout.print(v .. " ")
        }
        stdout.println("")
    }
}

// ----------------------------------------------------------------------------
// 4.  Classes: fields, constructors, inheritance, super
// ----------------------------------------------------------------------------

class Animal {

    public name: String

    public static new(name: String): Self {
        return Self { name: name, }
    }

    public speak(): String {
        return "..."
    }

    public describe(): String {
        return self.name .. " says " .. speak()
    }
}

class Dog extends Animal {

    override public speak(): String {
        return "woof"
    }
}

class Cls {

    public static demo(): Void {
        a: Animal = Dog.new("rex")
        stdout.println(a.describe())
        d: Dog = Dog.new("fido")
        stdout.println(d.name)
    }
}

// ----------------------------------------------------------------------------
// 5.  Interfaces: implements, default methods, dynamic dispatch
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

    override public greeting(): String {
        return "bot"
    }
}

class PoliteBot extends Bot {

    override public greeting(): String {
        return "polite bot"
    }
}

class Ifaces {

    public static demo(): Void {
        g: Greetable = PoliteBot.new()
        stdout.println(g.greeting())
        stdout.println(g.farewell())
        b: Bot = Bot.new()
        stdout.println(b.farewell())
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

    public first: A
    public second: B

    public static new(first: A, second: B): Self {
        return Self { first: first, second: second, }
    }

    public swap(): Pair<B, A> {
        return Pair<B, A>.new(self.second, self.first)
    }
}

class Gen {

    public static demo(): Void {
        b: Box<Long> = Box<Long>.new(41)
        b.set(42)
        stdout.println(b.get())
        s: Box<String> = Box<String>.new("hi")
        stdout.println(s.get())
        p: Pair<Long, String> = Pair<Long, String>.new(7, "seven")
        q: Pair<String, Long> = p.swap()
        stdout.println(q.first .. "=" .. q.second)
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
        c: Color = Color.red
        d: Color = Color.blue(255)
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
        x: List<Long> = [1, 2, 3]
        x.add(4)
        x.set(0, 10)
        stdout.println(x.get(0))
        stdout.println(x.contains(3))
        stdout.println(x.join(","))

        m: Map<String, Long> = { "a": 1, "b": 2 }
        m.put("c", 3)
        stdout.println(m.size())
        stdout.println(m.get("a"))
        for k in m {
            stdout.print(k .. "=" .. m.get(k) .. " ")
        }
        stdout.println("")

        st: Stack<Long> = Stack<Long>.new()
        st.push(1)
        st.push(2)
        stdout.println(st.pop())
        stdout.println(st.peek())

        u: Set<Long> = Set<Long>.new()
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
        t: Thread = Thread.new(Counter.new(3))
        t.start()
        t.join()
        stdout.println("joined")

        mu: Mutex = Mutex.new()
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
        m: Map<String, Object> = { "k": 1 }
        stdout.println(Json.stringify(m))
        now: Long = Time.now()
        stdout.println(now > 0)
    }
}

// ----------------------------------------------------------------------------
// 12. Introspection
// ----------------------------------------------------------------------------

class Introspect {

    public static demo(): Void {
        a: Long = 5
        b: String = "text"
        o: Object = a
        stdout.println(Type.of(a))
        stdout.println(Type.of(b))
        stdout.println(Type.isType(o, "Long"))
        stdout.println(Type.isType(b, "String"))
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
        Ifaces.demo()
        Gen.demo()
        Enums.demo()
        Excs.demo()
        Colls.demo()
        Conc.demo()
        Stdlib.demo()
        Introspect.demo()
        return 0
    }
}
