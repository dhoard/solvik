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
        System.getOut().println(count)

        let pi: Double = 3.14159
        System.getOut().println(pi * 2.0)

        let ok: Boolean = true && false || true
        System.getOut().println(ok)

        let ch: Char = 'A'
        System.getOut().println(ch)

        // Conversions go through <Type>.from(...).
        System.getOut().println(Long.from("7"))
        System.getOut().println(Double.from(3))
        System.getOut().println(String.from(99))
        System.getOut().println(Boolean.from("true"))
        System.getOut().println(Byte.from(7))
        System.getOut().println(Short.from(300))
        System.getOut().println(Integer.from(100000))
        System.getOut().println(Float.from(1.5))
        System.getOut().println(Char.from('x'))
        System.getOut().println(BigInteger.from("123456789012345678901234567890"))
        System.getOut().println(BigDecimal.from("1.5"))

        // Nullability and coalesce.
        let n: Long? = null
        let m: Long? = 5
        System.getOut().println(n == null)
        System.getOut().println(n ?? 10)
        System.getOut().println(m ?? 10)

        // Null narrowing: inside a != null branch the binding is seen as
        // non-nullable, so arithmetic is allowed without a coalesce.
        if m != null {
            System.getOut().println("narrowed " .. (m + 1))
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
        System.getOut().println(dec == 1234567)
        System.getOut().println(hex == 255)
        System.getOut().println(oct == 15)
        System.getOut().println(bin == 5)
        System.getOut().println(sep == 1000000)

        // Float literals: fraction and exponent forms; f/F selects Float,
        // d/D Double, bd/BD BigDecimal (exact decimal text).
        let f1: Double = 2.5
        let f2: Double = 1e3
        let f3: Double = -2.5e-1
        System.getOut().println(f1 + f2 + f3)
        let sf: Float = 2.5f
        let sd: Double = 2.5d
        let sbd: BigDecimal = 1.5bd
        System.getOut().println(sf * 2.0f)
        System.getOut().println(sd * 2.0d)
        System.getOut().println(sbd + 0.5bd)

        // Unsuffixed integer literals are Integer when they fit 32 bits,
        // Long when they fit 64 bits, BigInteger beyond that.
        let small: Integer = 42
        let big: Long = 5_000_000_000
        let huge: BigInteger = 123456789012345678901234567890
        System.getOut().println(small + big)
        System.getOut().println(huge * BigInteger.from(2))

        // String escapes: \t \xHH \uHHHH \u{...}.
        System.getOut().println("tab\there")
        System.getOut().println("\x41\u0042\u{43}")
        // Remaining escapes, verified by equality against unicode forms
        // (printing raw NUL/carriage returns would be noisy).
        System.getOut().println("\r" == "\u000D")
        System.getOut().println("\0" == "\u0000")
        System.getOut().println("\\n" == "\u005cn")
        System.getOut().println("\"" == "\u0022")
        System.getOut().println("'" == "\u0027")
        System.getOut().println("\U0001F600" == "\u{1F600}")
        // Raw strings disable escaping; r#"..."# allows embedded quotes;
        // r##"..."## raises the hash level past embedded # sequences.
        System.getOut().println(r"back\slash \n stays raw")
        System.getOut().println(r#"quote " inside"#)
        System.getOut().println(r##"one # two ## three"##)

        // Char escapes.
        let nl: Char = '\n'
        System.getOut().println(nl == '\n')
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
        let flag: Boolean = true
        System.getOut().println(neg * -1)
        System.getOut().println(!flag)

        // Compound assignments: -= *= /= %= ..=
        let mutable a: Long = 10
        a -= 4
        a *= 3
        a /= 2
        a %= 7
        System.getOut().println(a)   // ((10-4)*3)/2 % 7 == 2

        let mutable s: String = "ab"
        s ..= "cd"
        System.getOut().println(s)   // abcd

        // Semicolons remain accepted statement separators.
        let one: Long = 1; let two: Long = 2
        System.getOut().println(one + two)

        // Calls: positional, named, mixed, defaulted, out-of-order named.
        let p1: Point = Point.new(3, 4)
        let p2: Point = Point.new(x: 6, y: 8)
        let p3: Point = Point.new(1, y: 2)
        let p4: Point = Point.new()
        let p5: Point = Point.new(y: 5, x: 12)
        System.getOut().println(p1.dist())
        System.getOut().println(p2.dist())
        System.getOut().println(p3.dist())
        System.getOut().println(p4.dist())
        System.getOut().println(p5.dist())
    }
}

// ----------------------------------------------------------------------------
// 2.  Strings and regex
// ----------------------------------------------------------------------------

class Strs {

    public static demo(): Void {
        let s: String = "hello"
        System.getOut().println(s.length())
        System.getOut().println("foo" .. "bar")
        System.getOut().println(s.substring(1, 3))
        System.getOut().println(s.contains("ell"))
        System.getOut().println(s.startsWith("he"))
        System.getOut().println(s.endsWith("lo"))
        System.getOut().println(s.indexOf("l"))
        System.getOut().println(s.charAt(1))
        System.getOut().println("  pad  ".trim())
        System.getOut().println(s.toUpperCase())
        System.getOut().println(s.toLowerCase())
        let parts: List<String> = s.split("l")
        System.getOut().println(parts.size())
        System.getOut().println(s.replace("l", "L"))

        let r: Regex = Regex.new("[a-z]+")
        System.getOut().println(r.matches("abc"))
        System.getOut().println(r.find("123 abc 456"))
        let all: List<String> = r.all("one two three")
        System.getOut().println(all.size())
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
            System.getOut().println("big")
        } else {
            System.getOut().println("small")
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
            System.getOut().print(i .. " ")
        }
        System.getOut().println("")

        // range for-in
        let mutable total: Long = 0
        for n in 1..6 {
            total += n
        }
        System.getOut().println(total)

        // for-in over a list
        let xs: List<Long> = [10, 20, 30]
        for v in xs {
            System.getOut().print(v .. " ")
        }
        System.getOut().println("")

        // for-in over a Stack (index order, bottom to top) and a String
        // (chars).
        let stk: Stack<Long> = Stack<Long>.new()
        stk.push(1)
        stk.push(2)
        for v in stk {
            System.getOut().print(v .. " ")
        }
        System.getOut().println("")
        for ch in "abc" {
            System.getOut().print(ch .. " ")
        }
        System.getOut().println("")

        // else-if chain
        let grade: Long = 87
        if grade >= 90 {
            System.getOut().println("A")
        } else if grade >= 80 {
            System.getOut().println("B")
        } else if grade >= 70 {
            System.getOut().println("C")
        } else {
            System.getOut().println("F")
        }

        // switch: multi-value cases, default, case-body scoping
        let day: Long = 6
        switch day {
            case 1, 2, 3, 4, 5: {
                System.getOut().println("workday")
            }
            case 6, 7: {
                System.getOut().println("weekend")
            }
            default: {
                System.getOut().println("invalid day")
            }
        }

        // Standalone scope blocks create a fresh name scope; break resolves
        // through them to the nearest enclosing loop.
        {
            let inner: Long = 1
            System.getOut().println("scoped " .. inner)
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
        System.getOut().println("scope loop " .. k)
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
        System.getOut().println(e.name())
        System.getOut().println(e.title())
        // External field access is a compile error: System.getOut().println(e.person)
        let p: Named = e
        System.getOut().println(p.name())

        // Multiple delegates: each interface is forwarded to its own field.
        let r: Registered = Registered.new("Grace", 1001)
        System.getOut().println(r.name())
        System.getOut().println(r.id())
        // The private delegate fields remain inaccessible:
        //   System.getOut().println(r.person)
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

// Static fields are private to their declaring class and accessed only
// through type-qualified names: Ticker.total and Self.total are equivalent
// here. A class's static field initializers (in declaration order) and its
// single static block form one unit that runs exactly once, lazily,
// immediately before the class's first active use (static field access,
// static method call, or object construction). Inside the block, static
// members of the declaring class resolve by bare name.
class Ticker {

    static count: Long = 0
    static mutable total: Long = 0
    static limit: Long = 10

    static {
        // Runs exactly once, at Ticker's first active use below, after
        // every static field initializer of this class has completed.
        total += 5
        System.getOut().println("static block total=" .. String.from(total))
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
        // The first active use of Ticker: its field initializers and block
        // run now (the "static block" line appears here, not at startup).
        let a: Ticker = Ticker.new()
        let b: Ticker = Ticker.new()
        Ticker.tick()
        Ticker.tick()
        // Later accesses do not rerun the block: both instances observe the
        // same shared slot (5 from the static block plus two ticks).
        System.getOut().println("static shared " .. a.current())
        System.getOut().println("static limit " .. b.current())
    }
}

// Never actively used: its block would print if initialization were eager.
// Lazy initialization means this program produces no output from it.
class NeverUsed {

    static {
        System.getOut().println("never used")
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
        System.getOut().println(g.greeting())
        System.getOut().println(g.farewell())
        let b: Bot = Bot.new()
        System.getOut().println(b.farewell())
        let l: LoggingBot = LoggingBot.new()
        System.getOut().println(l.greeting())
        System.getOut().println(l.farewell())

        // Interface extension: Tag satisfies both Labeled and Named.
        let t: Labeled = Tag.new("ada", "core")
        System.getOut().println(t.describe())
        let tn: Named = t
        System.getOut().println(tn.name())

        // Generic interface with a default method.
        let sb: Boxed<Long> = SevenBox.new()
        System.getOut().println(sb.dup())

        // Diamond defaults: the most-specific unambiguous default wins...
        let ol: Left = OnlyLeft.new()
        System.getOut().println(ol.kind())
        // ...and an explicit class method beats competing defaults.
        let bs: BothSides = BothSides.new()
        System.getOut().println(bs.kind())
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
        System.getOut().println(b.get())
        let s: Box<String> = Box<String>.new("hi")
        System.getOut().println(s.get())
        let p: Pair<Long, String> = Pair<Long, String>.new(7, "seven")
        let q: Pair<String, Long> = p.swap()
        System.getOut().println(q.first() .. "=" .. q.second())

        // Generic methods with inferred type arguments.
        let r: Long = Ids.identity(99)
        System.getOut().println(r)
        let w: String = Ids.identity("kept")
        System.getOut().println(w)

        // Constrained generic method: Person satisfies the Named bound.
        let ada: Person = Person.new("Ada Lovelace")
        let top: Named = Ids.pick(ada)
        System.getOut().println(top.name())
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
            Color.red => System.getOut().println("red")
            Color.green => System.getOut().println("green")
            Color.blue(r) => System.getOut().println("blue " .. r)
            _ => System.getOut().println("?")
        }
        match d {
            Color.blue(r) => System.getOut().println("got " .. r)
            _ => System.getOut().println("not blue")
        }

        // Enum values compare by variant identity (and payload equality).
        System.getOut().println(c == Color.red)
        System.getOut().println(c != d)

        // Literal patterns on a primitive subject.
        let code: Long = 2
        match code {
            1 => System.getOut().println("one")
            2 => System.getOut().println("two")
            _ => System.getOut().println("many")
        }

        // Nested list pattern with bindings.
        let pair: List<Long> = [3, 4]
        match pair {
            [a, b] => System.getOut().println("pair " .. a .. "+" .. b)
            _ => System.getOut().println("not a pair")
        }

        // Literal patterns: float, bool, char, string.
        let ratio: Double = 1.5
        match ratio {
            1.5 => System.getOut().println("ratio")
            _ => System.getOut().println("other ratio")
        }
        let flag: Boolean = true
        match flag {
            true => System.getOut().println("flag set")
            false => System.getOut().println("flag clear")
        }
        let zed: Char = 'z'
        match zed {
            'z' => System.getOut().println("zed")
            _ => System.getOut().println("not zed")
        }
        let word: String = "hi"
        match word {
            "hi" => System.getOut().println("greeting")
            _ => System.getOut().println("unknown")
        }

        // Generic enum variants carry the instantiated payload type.
        let v: Verdict<Long> = Verdict<Long>.pass(1)
        match v {
            Verdict.pass(score) => System.getOut().println("pass " .. score)
            Verdict.fail(reason) => System.getOut().println("fail " .. reason)
        }
    }
}

// ----------------------------------------------------------------------------
// 8.  Exceptions
// ----------------------------------------------------------------------------

class Cancelled {

    public static new(): Self {
        return Self {}
    }
}

class Excs {

    public static demo(): Void {
        // Throwing requires an Exception (or class/interface value); the
        // built-in Exception.new carries a message.
        try {
            throw Exception.new("boom")
        } catch (e: Exception) {
            System.getOut().println("caught " .. e)
        }
        try {
            System.getOut().println("work")
        } finally {
            System.getOut().println("cleaned")
        }
        // An exception propagates through a finally without catch.
        try {
            try {
                throw Exception.new("deep")
            } finally {
                System.getOut().println("inner finally")
            }
        } catch (e: Exception) {
            System.getOut().println("outer caught " .. e)
        }
        // Multiple typed clauses are tested in order; a user-defined class
        // value may be thrown too, and its clause wins over the generic
        // Exception clause.
        try {
            throw Cancelled.new()
        } catch (e: Cancelled) {
            System.getOut().println("cancelled")
        } catch (e: Exception) {
            System.getOut().println("never reached")
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
        System.getOut().println(x.get(0))
        System.getOut().println(x.contains(3))
        System.getOut().println(x.join(","))

        let m: Map<String, Long> = { "a": 1, "b": 2 }
        m.put("c", 3)
        System.getOut().println(m.size())
        System.getOut().println(m.get("a"))
        for k in m {
            System.getOut().print(k .. "=" .. m.get(k) .. " ")
        }
        System.getOut().println("")

        let st: Stack<Long> = Stack<Long>.new()
        st.push(1)
        st.push(2)
        System.getOut().println(st.pop())
        System.getOut().println(st.peek())

        let u: Set<Long> = Set<Long>.new()
        u.add(5)
        u.add(6)
        u.add(5)
        System.getOut().println(u.size())
        System.getOut().println(u.contains(5))
        u.remove(5)
        System.getOut().println(u.contains(5))
        u.clear()
        System.getOut().println(u.isEmpty())

        // Java-shaped Set API: Boolean add/remove, addAll/containsAll/toList.
        let s1: Set<Long> = Set<Long>.withCapacity(4)
        System.getOut().println(s1.add(1))
        System.getOut().println(s1.add(1))
        let s2: Set<Long> = Set<Long>.new()
        s2.add(2)
        s2.add(3)
        System.getOut().println(s1.addAll(s2))
        System.getOut().println(s1.containsAll(s2))
        System.getOut().println(s1.remove(3))
        System.getOut().println(s1.containsAll(s2))
        let members: List<Long> = s1.toList()
        System.getOut().println(members.size())

        // for-in over a Set: every member is visited exactly once, in
        // unspecified (hash) order. Collect and sort to observe the members
        // deterministically.
        let seen: List<Long> = List<Long>.new()
        for v in s1 {
            seen.add(v)
        }
        seen.sort()
        System.getOut().println(seen.join(","))

        // Remaining List built-ins.
        let l2: List<Long> = List<Long>.new()
        System.getOut().println(l2.isEmpty())
        l2.add(3)
        l2.add(1)
        l2.add(2)
        l2.sort()
        System.getOut().println(l2.get(0))
        System.getOut().println(l2.indexOf(2))
        l2.reverse()
        System.getOut().println(l2.get(0))
        l2.remove(0)
        System.getOut().println(l2.size())
        l2.clear()
        System.getOut().println(l2.isEmpty())

        // Java-shaped List API: addAt / removeValue / reversed / addAll.
        let l3: List<Long> = List<Long>.withCapacity(8)
        l3.addAt(0, 1)
        l3.addAt(1, 3)
        l3.addAt(1, 2)
        System.getOut().println(l3.join(","))
        let old: Long = l3.set(0, 10)
        System.getOut().println(old)
        let gone: Long = l3.remove(0)
        System.getOut().println(gone)
        System.getOut().println(l3.removeValue(99))
        System.getOut().println(l3.removeValue(3))
        let rev: List<Long> = l3.reversed()
        System.getOut().println(rev.join(","))
        let more: List<Long> = [7, 8]
        l3.addAll(more)
        System.getOut().println(l3.join(","))

        // Remaining Map built-ins.
        let m2: Map<String, Long> = Map<String, Long>.new()
        System.getOut().println(m2.isEmpty())
        m2.put("x", 1)
        System.getOut().println(m2.containsKey("x"))
        let ks: List<String> = m2.keys()
        let vs: List<Long> = m2.values()
        System.getOut().println(ks.size())
        System.getOut().println(vs.size())
        m2.remove("x")
        System.getOut().println(m2.isEmpty())
        m2.put("y", 2)
        m2.clear()
        System.getOut().println(m2.size())

        // Java-shaped Map API: nullable get/put/replace, atomic compounds.
        let m3: Map<String, Long> = Map<String, Long>.withCapacity(4)
        let absent: Long? = m3.get("nope")
        System.getOut().println(absent == null)
        let prev: Long? = m3.put("a", 1)
        System.getOut().println(prev == null)
        let prev2: Long? = m3.put("a", 2)
        System.getOut().println(prev2 == 1)
        System.getOut().println(m3.getOrDefault("a", 42))
        System.getOut().println(m3.getOrDefault("zz", 42))
        System.getOut().println(m3.putIfAbsent("a", 9) == null)
        System.getOut().println(m3.replace("a", 3) == 2)
        System.getOut().println(m3.replace("zz", 3) == null)
        System.getOut().println(m3.containsValue(3))
        System.getOut().println(m3.removeMapping("a", 99))
        System.getOut().println(m3.removeMapping("a", 3))
        let src: Map<String, Long> = { "p": 5, "q": 6 }
        m3.putAll(src)
        System.getOut().println(m3.size())
        System.getOut().println(m3.remove("p") == 5)

        // Remaining Stack built-ins.
        let st2: Stack<Long> = Stack<Long>.new()
        st2.push(9)
        System.getOut().println(st2.size())
        System.getOut().println(st2.isEmpty())

        // Deque-style Stack API.
        let dq: Stack<Long> = Stack<Long>.withCapacity(4)
        dq.addFirst(1)
        dq.addLast(3)
        dq.push(2)
        System.getOut().println(dq.peekFirst() == 1)
        System.getOut().println(dq.peekLast() == 2)
        System.getOut().println(dq.removeFirst() == 1)
        System.getOut().println(dq.pop() == 2)
        System.getOut().println(dq.poll() == 3)
        System.getOut().println(dq.peek() == null)
        System.getOut().println(dq.poll() == null)
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
        System.getOut().println("worker up to " .. self.target)
    }
}

class Conc {

    public static demo(): Void {
        let t: Thread = Thread.new(Counter.new(3))
        t.start()
        t.join()
        System.getOut().println("joined")

        let mu: Mutex = Mutex.new()
        mu.lock()
        mu.unlock()
        System.getOut().println("mutex ok")

        let sem: Semaphore = Semaphore.new(2)
        sem.acquire()
        sem.release()
        System.getOut().println("semaphore ok")
    }
}

// ----------------------------------------------------------------------------
// 11. Standard library: deterministic digests, base64, json, math
// ----------------------------------------------------------------------------

class Stdlib {

    public static demo(): Void {
        System.getOut().println(Math.sqrt(16.0))
        System.getOut().println(Math.pow(2.0, 8.0))
        System.getOut().println(Math.abs(-3.5))
        System.getOut().println(Math.min(1.5, 2.5))
        System.getOut().println(Math.max(1.5, 2.5))
        System.getOut().println(Math.floor(2.7))
        System.getOut().println(Math.ceil(2.1))
        System.getOut().println(Math.round(2.5))
        System.getOut().println(Base64.encode("solvik"))
        System.getOut().println(Base64.decode(Base64.encode("round trip")))
        System.getOut().println(Hash.md5("abc"))
        System.getOut().println(Hash.sha1("abc"))
        System.getOut().println(Hash.sha256("abc"))
        let m: Map<String, Object> = { "k": 1 }
        let j: String = Json.stringify(m)
        System.getOut().println(j)
        let parsed: Object = Json.parse(j)
        System.getOut().println(parsed)
        let now: Long = Time.now()
        System.getOut().println(now > 0)
        Time.sleep(0)
        Random.seed(42)
        System.getOut().println(Random.nextLong(1000))
        System.getOut().println(Random.nextDouble() >= 0.0)
        Test.assert(true, "assert should hold")
        Test.assertEqual(2 + 2, 4)
        let rr: Regex = Regex.new("o+")
        System.getOut().println(rr.replace("too many loops", "0"))
        System.getErr().print("err stream ok")

        // File: write/read/exists/delete round trip (self-cleaning).
        let path: String = "example-tmp.txt"
        File.write(path, "round trip")
        System.getOut().println(File.exists(path))
        System.getOut().println(File.read(path))
        File.delete(path)
        System.getOut().println(File.exists(path))

        // Process: run a command, wait, inspect exit code and output.
        let noArgs: List<String> = []
        let p: Process = Process.new("printf done", noArgs)
        p.start()
        p.wait()
        System.getOut().println(p.exitCode())
        System.getOut().println(p.stdout().readAll())
    }
}

// ----------------------------------------------------------------------------
// 11b. System: process and runtime services
//
//      System is the process-wide service namespace: standard streams
//      (getIn/getOut/getErr), the line separator, the host environment,
//      runtime clocks, and the program-local property store. Time remains
//      the dedicated time namespace; System.getCurrentTimeMillis() shares
//      its wall-clock implementation rather than adding a second source.
//
//      Launch properties: run `solvik -Dmode=demo example.sol` (or
//      `./example -Dmode=demo` on a packaged executable) to initialize the
//      property store before any user code runs. The default invocation
//      below stays deterministic either way.
// ----------------------------------------------------------------------------

class SystemDemo {

    public static demo(): Void {
        // Standard streams are method accessors returning fresh handles.
        // getOut/getErr are used throughout this file; getIn reads stdin.
        System.getOut().println("streams " .. (System.getIn() != null))
        System.getErr().print("")

        // Wall clock: positive on normal Unix-epoch hosts (no raw timestamp).
        System.getOut().println(System.getCurrentTimeMillis() > 0)

        // Monotonic clock: compare differences, never the absolute value.
        let start: Long = System.getNanoTime()
        let elapsed: Long = System.getNanoTime() - start
        System.getOut().println(elapsed >= 0)

        // The line delimiter used by Writer.println(): LF.
        System.getOut().println(System.getLineSeparator() == "\n")

        // Program-local properties: previous-value returns, fallbacks,
        // clearing, and the empty-value/clearing distinction.
        let first: String? = System.setProperty("example.key", "one")
        System.getOut().println(first == null)
        let second: String? = System.setProperty("example.key", "two")
        System.getOut().println(second == "one")
        System.getOut().println(System.getProperty("example.key") == "two")
        System.getOut().println(System.getProperty("example.key", "fb") == "two")
        System.getOut().println(System.getProperty("example.missing") == null)
        System.getOut().println(System.getProperty("example.missing", "fb") == "fb")
        let cleared: String? = System.clearProperty("example.key")
        System.getOut().println(cleared == "two")
        System.getOut().println(System.getProperty("example.key") == null)
        System.setProperty("example.empty", "")
        System.getOut().println(System.getProperty("example.empty") == "")
        System.getOut().println(System.getProperty("example.empty") != null)
        System.clearProperty("example.empty")

        // A launch property supplied with -Dmode=demo, with a deterministic
        // fallback for the plain `solvik example.sol` invocation.
        let mode: String = System.getProperty("mode", "default")
        System.getOut().println(mode == "default" || mode == "demo")

        // Host environment: named lookup is nullable; the zero-argument form
        // is a non-null mutable snapshot. No particular variable may exist,
        // so only booleans are printed.
        let probe: String? = System.getEnv("SOLVIK_EXAMPLE_PROBE")
        System.getOut().println(probe == null || probe != null)
        let probeValue: String = System.getEnv("SOLVIK_EXAMPLE_PROBE") ?? "absent"
        System.getOut().println(probeValue == "absent" || probeValue.length() > 0)
        let env: Map<String, String> = System.getEnv()
        System.getOut().println(env != null)
        // Mutating the snapshot never touches the host environment.
        env.put("SOLVIK_EXAMPLE_PROBE", "injected")
        System.getOut().println(System.getEnv("SOLVIK_EXAMPLE_PROBE") == probe)
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
        System.getOut().println(Type.of(a))
        System.getOut().println(Type.of(b))
        System.getOut().println(Type.isType(o, "Long"))
        System.getOut().println(Type.isType(b, "String"))

        // Universal object contract: toString / equals / hashCode on every
        // reference value. Collections use structural equality, so equal
        // contents imply equal hashes; equals(null) is always false.
        let l1: List<Long> = [1, 2]
        let l2: List<Long> = [1, 2]
        System.getOut().println(l1.equals(l2))
        System.getOut().println(l1.hashCode() == l2.hashCode())
        System.getOut().println(l1.equals(null))
        System.getOut().println("text".toString() == b)
    }
}

// ----------------------------------------------------------------------------
// 13. Block scoping (Java-style name rules: no shadowing)
// ----------------------------------------------------------------------------

class Scope {

    public static demo(): Void {
        // Redeclaring a visible name is a compile error (C240); Solvik has
        // no shadowing. Distinct names are used per scope instead.
        let x: Long = 1
        let x2: Long = 2
        System.getOut().println(x + x2)   // 3

        // Block scoping: a binding declared inside a block is hidden after
        // it; loop/catch locals do not leak past their body.
        let z: Long = 100
        if true {
            let inner: Long = 200
            System.getOut().println(inner)   // 200
        }
        System.getOut().println(z)   // 100 (inner never visible here)

        let w: Long = 5
        for item in [1, 2] {
            System.getOut().print(item .. " ")   // 1 2
        }
        System.getOut().println("")
        System.getOut().println(w)   // 5 (loop variable never touched w)
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
        let flag: Boolean = true
        if flag
        {
            System.getOut().println("allman yes")
        }
        else
        {
            System.getOut().println("allman no")
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
        System.getOut().println(Varargs.sum(1, 2, 3))
        System.getOut().println(Varargs.sum())
        let vs: List<Long> = [4, 5]
        System.getOut().println(Varargs.sum(...vs))
    }
}

// ----------------------------------------------------------------------------
// Entry point
// ----------------------------------------------------------------------------

class Main {

    public static run(args: String...): Long {
        // Marker printed before any user class with a static block is
        // actively used: no "static block" or "never used" line may appear
        // above this point.
        System.getOut().println("entering Main")
        // The variadic entry-point argument list is an ordinary List.
        System.getOut().println("args=" .. args.size())
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
        SystemDemo.demo()
        Introspect.demo()
        Scope.demo()
        Allman.demo()
        System.getOut().println(AllmanClass.answer())
        return 0
    }
}
