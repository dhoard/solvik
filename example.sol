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
    pub static demo(): Void {
        mut count: Int = 42
        count += 1
        stdout.println(count)

        pi: Float = 3.14159
        stdout.println(pi * 2.0)

        ok: Bool = true && false || true
        stdout.println(ok)

        ch: Char = 'A'
        stdout.println(ch)

        // Conversions go through <Type>::from(...).
        stdout.println(Int::from("7"))
        stdout.println(Float::from(3))
        stdout.println(String::from(99))
        stdout.println(Bool::from(0))

        // Nullability and coalesce.
        n: Int? = null
        m: Int? = 5
        stdout.println(n == null)
        stdout.println(n ?? 10)
        stdout.println(m ?? 10)
    }
}

// ----------------------------------------------------------------------------
// 2.  Strings and regex
// ----------------------------------------------------------------------------

class Strs {
    pub static demo(): Void {
        s: String = "hello"
        stdout.println(s.length())
        stdout.println("foo" .. "bar")
        stdout.println(s.substring(1, 3))
        stdout.println(s.contains("ell"))
        stdout.println(s.toUpperCase())
        parts: List<String> = s.split("l")
        stdout.println(parts.size())
        stdout.println(s.replace("l", "L"))

        r: Regex = Regex::new("[a-z]+")
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
    pub static demo(): Void {
        // if / else
        x: Int = 7
        if x > 5 {
            stdout.println("big")
        } else {
            stdout.println("small")
        }

        // while with break / continue
        mut i: Int = 0
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
        mut total: Int = 0
        for n in 1..6 {
            total += n
        }
        stdout.println(total)

        // for-in over a list
        xs: List<Int> = [10, 20, 30]
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
    pub name: String

    pub static new(name: String): Self {
        return Self { name }
    }

    pub speak(): String {
        return "..."
    }

    pub describe(): String {
        return name .. " says " .. speak()
    }
}

class Dog extends Animal {
    override pub speak(): String {
        return "woof"
    }
}

class Cls {
    pub static demo(): Void {
        a: Animal = Dog::new("rex")
        stdout.println(a.describe())
        d: Dog = Dog::new("fido")
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
    pub static new(): Self {
        return Self {}
    }

    override pub greeting(): String {
        return "bot"
    }
}

class PoliteBot extends Bot {
    override pub greeting(): String {
        return "polite bot"
    }
}

class Ifaces {
    pub static demo(): Void {
        g: Greetable = PoliteBot::new()
        stdout.println(g.greeting())
        stdout.println(g.farewell())
        b: Bot = Bot::new()
        stdout.println(b.farewell())
    }
}

// ----------------------------------------------------------------------------
// 6.  Generics
// ----------------------------------------------------------------------------

class Box<T> {
    value: T

    pub static new(value: T): Self {
        return Self { value }
    }

    pub get(): T {
        return value
    }

    pub set(v: T): Void {
        value = v
    }
}

class Pair<A, B> {
    pub first: A
    pub second: B

    pub static new(first: A, second: B): Self {
        return Self { first, second }
    }

    pub swap(): Pair<B, A> {
        return Pair<B, A>::new(second, first)
    }
}

class Gen {
    pub static demo(): Void {
        b: Box<Int> = Box<Int>::new(41)
        b.set(42)
        stdout.println(b.get())
        s: Box<String> = Box<String>::new("hi")
        stdout.println(s.get())
        p: Pair<Int, String> = Pair<Int, String>::new(7, "seven")
        q: Pair<String, Int> = p.swap()
        stdout.println(q.first .. "=" .. q.second)
    }
}

// ----------------------------------------------------------------------------
// 7.  Enums and pattern matching
// ----------------------------------------------------------------------------

enum Color {
    Red
    Green
    Blue(Int)
}

class Enums {
    pub static demo(): Void {
        c: Color = Color::Red
        d: Color = Color::Blue(255)
        match c {
            Color::Red => stdout.println("red")
            Color::Green => stdout.println("green")
            Color::Blue(r) => stdout.println("blue " .. r)
            _ => stdout.println("?")
        }
        match d {
            Color::Blue(r) => stdout.println("got " .. r)
            _ => stdout.println("not blue")
        }
    }
}

// ----------------------------------------------------------------------------
// 8.  Exceptions
// ----------------------------------------------------------------------------

class Excs {
    pub static demo(): Void {
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
    pub static demo(): Void {
        x: List<Int> = [1, 2, 3]
        x.add(4)
        x.set(0, 10)
        stdout.println(x.get(0))
        stdout.println(x.contains(3))
        stdout.println(x.join(","))

        m: Map<String, Int> = { "a": 1, "b": 2 }
        m.put("c", 3)
        stdout.println(m.size())
        stdout.println(m.get("a"))
        for k in m {
            stdout.print(k .. "=" .. m.get(k) .. " ")
        }
        stdout.println("")

        st: Stack<Int> = Stack<Int>::new()
        st.push(1)
        st.push(2)
        stdout.println(st.pop())
        stdout.println(st.peek())

        u: Set<Int> = Set<Int>::new()
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
    target: Int

    pub static new(target: Int): Self {
        return Self { target }
    }

    pub run(): Void {
        stdout.println("worker up to " .. target)
    }
}

class Conc {
    pub static demo(): Void {
        t: Thread = Thread::new(Counter::new(3))
        t.start()
        t.join()
        stdout.println("joined")

        mu: Mutex = Mutex::new()
        mu.lock()
        mu.unlock()
        stdout.println("mutex ok")
    }
}

// ----------------------------------------------------------------------------
// 11. Standard library: deterministic digests, base64, json, math
// ----------------------------------------------------------------------------

class Stdlib {
    pub static demo(): Void {
        stdout.println(Math::sqrt(16.0))
        stdout.println(Math::pow(2.0, 8.0))
        stdout.println(Base64::encode("solvik"))
        stdout.println(Base64::decode(Base64::encode("round trip")))
        stdout.println(Hash::sha256("abc"))
        m: Map<String, Object> = { "k": 1 }
        stdout.println(Json::stringify(m))
        now: Int = Time::now()
        stdout.println(now > 0)
    }
}

// ----------------------------------------------------------------------------
// 12. Introspection
// ----------------------------------------------------------------------------

class Introspect {
    pub static demo(): Void {
        a: Int = 5
        b: String = "text"
        o: Object = a
        stdout.println(Type::of(a))
        stdout.println(Type::of(b))
        stdout.println(Type::isType(o, "Int"))
        stdout.println(Type::isType(b, "String"))
    }
}

// ----------------------------------------------------------------------------
// Entry point
// ----------------------------------------------------------------------------

class Main {
    pub static run(args: String...): Int {
        Prims::demo()
        Strs::demo()
        Flow::demo()
        Cls::demo()
        Ifaces::demo()
        Gen::demo()
        Enums::demo()
        Excs::demo()
        Colls::demo()
        Conc::demo()
        Stdlib::demo()
        Introspect::demo()
        return 0
    }
}
