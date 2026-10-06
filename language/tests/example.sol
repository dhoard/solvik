// A tour of every Solvik language construct and syntactic form
// (docs/LANGUAGE_SPEC.md specification version 2026.11-draft).
//
// Each numbered section prints its results; the golden `.output` file is the program's answer key.
// Deferred features (string interpolation, iteration protocols, safe casts, default parameters,
// overloading, `fallthrough`) are deliberately absent: they are not part of the language.

module example_app

// Compile-time file inclusion (section 20): one unaliased include from the default module and one
// aliased include of a named module, reached through the `::` namespace separator.
include "IncludeLibrary.sol"
include "ModulesLib.sol" alias math

// ---------------------------------------------------------------------------
// Declarations. Declaration lookup is order-independent (section 6), so executable
// statements below may reference anything in this file regardless of physical position.
// ---------------------------------------------------------------------------

// -- Values and the shared equality/hash pairing (sections 2, 3, 7) ----------

class Point {
    var x: Integer
    var y: Integer
    var mutable label: String

    Point(x: Integer, y: Integer, label: String) {
        this.x = x
        this.y = y
        this.label = label
    }

    override func toString(): String {
        return "Point(" .. this.x .. ";" .. this.label .. ")"
    }

    override func equals(other: Any?): Boolean {
        if (other is Point) {
            return this.x == other.x && this.y == other.y && this.label == other.label
        }
        return false
    }

    override func hashCode(): Integer {
        return 31 * (31 * this.x + this.y) + this.label.hashCode()
    }
}

// -- Inheritance: `mutable` classes, `mutable`/`override` chains, `abstract` bases (sections 7, 12)

mutable class Gear {
    var ratio: Integer

    Gear(ratio: Integer) {
        this.ratio = ratio
    }

    mutable func name(): String {
        return "gear:" .. this.ratio
    }

    // A bare name never resolves to a property (section 7); virtual dispatch reaches the override.
    func drive(): String {
        return this.name()
    }
}

mutable class Boosted extends Gear {
    Boosted() {
        super(8)
    }

    override mutable func name(): String {
        return "boosted-" .. super.name()
    }
}

class Chip extends Boosted {
    override func name(): String {
        return "chip-" .. super.name()
    }
}

abstract class Vehicle {
    var wheels: Integer

    Vehicle(wheels: Integer) {
        this.wheels = wheels
    }

    func summary(): String {
        return this.wheels .. " wheels"
    }
}

class Trike extends Vehicle {
    Trike() {
        super(3)
    }
}

// -- Interfaces: abstract members, default methods, multiple implementation (section 8)

interface Named {
    func name(): String

    func greeting(): String {
        return "Hello " .. name()
    }
}

interface Aged {
    func age(): Integer

    func shout(): String {
        return name() .. "!"
    }

    func name(): String {
        return "unknown"
    }
}

class Person implements Named, Aged {
    var fullName: String
    var years: Integer

    Person(fullName: String, years: Integer) {
        this.fullName = fullName
        this.years = years
    }

    // Both interfaces offer `name` (one abstract, one default); the class resolves it explicitly.
    func name(): String {
        return this.fullName
    }

    func age(): Integer {
        return this.years
    }
}

// -- Composition by delegation (section 9): the delegate type is a non-generic interface

interface Loud {
    func shout(text: String): String
}

class Echo implements Loud {
    func shout(text: String): String {
        return text .. "!!"
    }
}

class Announcer implements Loud {
    delegate var speaker: Loud

    Announcer(speaker: Loud) {
        this.speaker = speaker
    }
}

// -- Generics (section 11): generic classes, generic methods, generic functions

class Slot<T> {
    var mutable value: T

    Slot(value: T) {
        this.value = value
    }

    func get(): T {
        return this.value
    }

    func mapWith(transform: func(T): T): T {
        return transform(this.value)
    }
}

func pick<T>(first: T, other: T): T {
    return other
}

func render<T>(value: T): String {
    return value.toString()
}

// -- Enums: payloads, multiple payload values, payload-free variants (section 12)

enum Grade {
    Pass(Integer)
    Fail(Integer, String)
    Excused
}

func gradeText(grade: Grade): String {
    return match grade {
        Pass(score) => "pass " .. score
        Fail(score, reason) => "fail " .. score .. " (" .. reason .. ")"
        Excused => {
            print("excused|")
            "excused"
        }
    }
}

// -- The conventional Result enum: exhaustive match plus the synthesized operations (sections 12, 23)

enum Result<T, E> {
    Ok(T)
    Err(E)
}

func parse(raw: String): Result<Integer, String> {
    if (raw == "bad") {
        return Result.Err("cannot parse")
    }
    return Result.Ok(42)
}

func parseDoubled(raw: String): Result<Integer, String> {
    // The postfix `?` propagates an Err as an immediate return and unwraps an Ok.
    var value = parse(raw)?
    return Result.Ok(value + value)
}

// -- Unchecked exceptions (section 22)

class ValidationError extends RuntimeException {
}

mutable class ScaledError extends ApplicationException {
    var level: Integer

    ScaledError(level: Integer) {
        this.level = level
    }
}

func risky(raw: String): Integer {
    if (raw == "parse") {
        // The trailing `String?` argument is the synthesized message, not a declared parameter.
        throw ValidationError("bad input")
    }
    if (raw == "scale") {
        throw ScaledError(2, "too big")
    }
    if (raw == "escape") {
        throw ScaledError(1)
    }
    // No exception: an ordinary value completes the call normally.
    return 42
}

// -- Functions (section 6): value returns, Unit returns, recursion, generic instantiation

func add(a: Integer, b: Integer): Integer {
    return a + b
}

func shoutLine(text: String) {
    println(text .. "!")
}

func factorial(n: Integer): Integer {
    if (n <= 1) {
        return 1
    }
    return n * factorial(n - 1)
}

func scaleBy(factor: Integer): func(Integer): Integer {
    // Explicit immutable capture: `factor` is bound into the closure at creation.
    return func [factor](value: Integer): Integer {
        return value * factor
    }
}

class Tagger {
    var tag: String = "tag"

    func attach(value: Integer): String {
        return this.tag .. value.toString()
    }

    func bonusBy(bonus: Integer): func(Integer): Integer {
        // Both `this` and the immutable parameter must appear in the capture list.
        return func [this, bonus](value: Integer): Integer {
            return value + bonus
        }
    }

    var transform: func(Integer): Integer = func(value: Integer): Integer {
        return value - 1
    }
}

func describe(value: Any?): String {
    // Flow-sensitive null narrowing (section 5).
    if (value == null) {
        return "nothing"
    }
    return "value:" .. value.toString()
}

func classify(input: String): String {
    // Switch statement: constant and regex labels, source order, no fallthrough (section 13).
    switch (input) {
        case "zero" {
            return "zero"
        }

        case regex r#"^\d+$"# {
            return "number"
        }

        case regex r#"^[A-Za-z]+$"# {
            return "word"
        }

        default {
            return "other"
        }
    }
}

// A declaration that physically follows the executable statements is still visible to them.
func lateBound(n: Integer): String {
    // Expression `switch` (section 21.5): every normally completing case ends in a tail value.
    return switch (n) {
        case 0 {
            "zero"
        }

        case 1, 2 {
            "small"
        }

        default {
            "large"
        }
    }
}

// ---------------------------------------------------------------------------
// Executable top-level statements: the body of the implicit `main` (section 6).
// ---------------------------------------------------------------------------

// -- 1. Bindings and mutability (section 2) -----------------------------------

var name: String = "Solvik"
var inferred = 42
var mutable counter: Integer = 0
counter = counter + 7
// A semicolon separates two constructs sharing one physical line and never terminates one.
var one = 1; var two = 2; println(one + two)
// A call argument list may end with a trailing comma and spread across lines.
println(add(1, 2,))
var spread = add(1,
    41)
println(name .. " " .. inferred .. " " .. counter)
println(spread .. " " .. factorial(5))

// -- 2. Literals and numeric types (sections 1, 4) -----------------------------

println(2147483647)
var maxInt: Integer = 2147483647
// Integral arithmetic is checked and overflows raise a Solvik arithmetic error; an implicit
// widening to `Long` carries the value past the `Integer` range without loss (section 4).
var maxAsLong: Long = maxInt
println(maxAsLong + 1)
println(1L * 1000L)
println(1.5)
println(2.25f)
println(1.5f * 2.0f)
println(Byte(100))
println(Short(300))
// Explicit conversions cover every other numeric conversion.
println(Integer(3.9))
println(Long(41) + 1L)
println('A')
println('z')
// Integral division truncates toward zero; unary minus and Boolean operators.
println(7 / 2)
println(-5)
println(2 * 3 + 4)
println(1 < 2 && !(3 >= 4) || false)
// Expression continuation after a binary operator (section 16).
var total = 1 +
    2 +
    3
println(total)

// -- 3. Strings, raw strings, concatenation (sections 3, 15) -------------------

println("tab[\t]nl[\n]quote[\"]backslash[\\]native-escape[\N]")
var raw = r#"\d+ "quoted" C:\temp"#
println(raw)
var hashy = r##"contains "# text"##
println(hashy)
var sql = r#"SELECT 1"#
println(sql)
// `..` renders both operands through `toString`; it is not `+`, which stays numeric.
println("sum=" .. total .. ", flag=" .. (total > 3))
// A member chain continues across lines through a leading `.` (section 16).
var chained = "solvik"
    .toString()
    .toString()
println(chained)

// -- 4. Equality, identity, hashing (section 3) --------------------------------

var p1 = Point(1, 2, "first")
var p2 = Point(1, 2, "first")
var sameRef = p1
println(p1 == p2)
println(p1 != p2)
println(p1 === sameRef)
println(p1 !== p2)
println(p1.hashCode() == p2.hashCode())
// Overridden `toString` is honored by print, println, and concatenation.
println(p1)
println("held: " .. p1)
// `Any` never disables checking: read it back through a checked cast or a type test (section 18).
var erased: Any = p1
if (erased is Point) {
    println(erased.label)
}
var recast = erased as Point
println(recast.label)
// `var` freezes the binding, not the object: a `var mutable` property stays writable.
p1.label = "second"
println(p1 == p2)
p1.label = "first"

// -- 5. Inheritance and virtual dispatch (sections 7, 12) ----------------------

println(Gear(4).drive())
println(Boosted().drive())
println(Chip().drive())
var vehicle: Vehicle = Trike()
println(vehicle.summary())

// -- 6. Interfaces and delegation (sections 8, 9) ------------------------------

var person = Person("Dana", 30)
println(person.greeting())
println(person.shout())
var loud: Loud = Announcer(Echo())
println(loud.shout("hey"))

// -- 7. Generics (section 11) ---------------------------------------------------

var slot = Slot(7)
println(slot.get())
println(slot.mapWith(scaleBy(3)))
var texts: Slot<String> = Slot("abc")
println(texts.get())
// Explicit type arguments are permitted on direct calls.
println(pick<Integer>(1, 2))
println(pick("a", "b"))
println(render(99))
println(render('c'))

// -- 8. Enums and exhaustive `match` (section 12) -------------------------------

println(gradeText(Grade.Pass(95)))
println(gradeText(Grade.Fail(30, "late")))
print(gradeText(Grade.Excused))
println("")
println(Grade.Pass(95) == Grade.Pass(95))
println(Grade.Pass(95) == Grade.Excused)
// `match` over a class type needs a wildcard branch (section 12).
func area(shape: Vehicle): String {
    return match shape {
        trike: Trike => "trike " .. trike.summary()
        _ => "vehicle"
    }
}
println(area(Trike()))
println(area(VehicleHolder().make()))

class VehicleHolder {
    func make(): Vehicle {
        return Trike()
    }
}

// -- 9. Result values: operations, propagation, must-consume (section 23) -------

var good = parse("ok")
println(good.isOk())
println(good.unwrap())
var bad = parse("bad")
println(bad.isErr())
println(bad.unwrapErr())
println(good.expect("should be ok"))
var propagated = parseDoubled("ok")
println(propagated.unwrap())
println(parseDoubled("bad").unwrapErr())
// A Result must be consumed; `ignore()` is the deliberate discard.
good.ignore()
bad.ignore()

// -- 10. Nullability (section 5) -------------------------------------------------

var missing: String? = null
var present: String? = "here"
println(missing ?? "fallback")
println(present ?? "fallback")
println(missing?.toString())
// Reference-identity null tests narrow the same way as `== null`/`!= null` for
// identity-bearing types (section 3), and writes invalidate a prior narrowing (section 5).
var mutable holder: Point? = p1
if (holder !== null) {
    println(holder.label)
}
holder = null
println(holder === null)
println(describe(null))
println(describe(7))

// -- 11. Control flow (section 17) ------------------------------------------------

var mutable walked = ""
for (i in 1...3) {
    walked = walked .. i
}
for (i in 0..<3) {
    walked = walked .. i
}
for (i in 3..>0) {
    walked = walked .. i
}
println(walked)

var mutable loopTotal = 0
var mutable spins = 0
while (true) {
    spins = spins + 1
    if (spins == 2) {
        continue
    }
    loopTotal = loopTotal + spins
    if (spins > 4) {
        break
    }
}
println(loopTotal)

// An `if`/`else if`/`else` chain as an expression, with statements before the tail (section 21.4).
var band = if (counter > 100) {
    "high"
}
else if (counter > 5) {
    "medium"
}
else {
    "low"
}
println(band)

// A block expression: statements then a tail expression (section 21.2).
var computed = {
    var base = 20
    base + 22
}
println(computed)

// A stand-alone scope block introduces an independent scope and may shadow (section 6).
{
    var name = "inner"
    println(name)
}
{
    var name = "also inner"
    println(name)
}

// -- 12. switch statement and expression (sections 13, 21.5) ---------------------

println(classify("zero"))
println(classify("42"))
println(classify("hello"))
println(classify("!!"))
// A statement switch may omit `default` and do nothing when no label matches.
switch (99) {
    case 1 {
        println("not printed")
    }
}
// `switch` expressions and regex dispatch:
var kind = switch (counter) {
    case 0 {
        "none"
    }
    case 1, 2, 3 {
        "few"
    }
    default {
        "many"
    }
}
println(kind)
var viaRegex = switch (name) {
    case regex r#"^[a-z]+$"# {
        "lower"
    }
    default {
        "mixed"
    }
}
println(viaRegex)

println(lateBound(0))
println(lateBound(1))
println(lateBound(7))

// -- 13. Function values (section 6) ----------------------------------------------

// Named functions are values; module-qualified functions too. Every reference to one
// declaration is the same canonical value, and every function value renders as `func`.
var formatter: func(Integer): String = render
var doubled: func(Integer): Integer = scaleBy(2)
var doubler: func(Integer): Integer = math::double
println(formatter(3))
println(doubled(21))
println(doubler(21))
println(math::double(10))
println(scaleBy(3)(3))

// Anonymous functions and explicit capture.
var plusOne: func(Integer): Integer = func(value: Integer): Integer {
    return value + 1
}
println(plusOne(41))
var offset: func(Integer): Integer = scaleBy(10)
println(offset(4))
var tagger = Tagger()
var attach: func(Integer): String = tagger.attach
println(attach(7))
var withBonus: func(Integer): Integer = tagger.bonusBy(100)
println(withBonus(1) == withBonus(1))
println(withBonus(1))
// A property may itself hold a function value.
println(tagger.transform(9))
// Bound references are fresh identities; named references are canonical.
println(attach === tagger.attach)
var aliased: func(Integer): String = tagger.attach
println(attach.equals(aliased))
// The predeclared `println` is itself a function value.
var output: func(Any?): Unit = println
output("via-value")
println(plusOne.toString())
println(formatter === formatter)
// Nullable function values refine like any other nullable value.
var mutable optional: (func(Integer): Integer)? = null
if (optional != null) {
    println("no")
}
else {
    println("fn-null")
}
optional = plusOne
if (optional != null) {
    println(optional(9))
}
// A generic function used as a value is instantiated to the type its position expects.
var integerRender: func(Integer): String = render
println(integerRender(8))
// Function values flow through parameters and results.
func applyTwice(operation: func(Integer): Integer, value: Integer): Integer {
    return operation(operation(value))
}
println(applyTwice(plusOne, 40))
// A generic function used as a value can also produce closures through a factory type.
var factory: func(Integer): func(Integer): Integer = scaleBy
println(factory(2)(40))

// -- 14. Collections (section 11) ---------------------------------------------------

var items: List<Integer> = List(1, 2, 3)
items.add(4)
items.set(0, 9)
println(items.size)
println(items.get(0))
println(items.removeAt(1))
println(items.isEmpty)
var unique: Set<Integer> = Set(1, 2, 2)
println(unique.size)
println(unique.add(3))
println(unique.contains(2))
println(unique.remove(1))
var scores: Map<String, Integer> = Map("a": 1, "b": 2)
scores.put("a", 10)
println(scores.size)
println(scores.get("a"))
println(scores.containsKey("c"))
println(scores.remove("b"))
var stack: Stack<String> = Stack()
stack.push("one")
stack.push("two")
println(stack.peek())
println(stack.pop())
println(stack.size)
// Explicit type arguments on construction; inferred otherwise.
var explicit: List<Integer> = List<Integer>(7)
println(explicit.get(0))

// -- 15. Regex (section 14) -----------------------------------------------------------

var email = Regex(r#"(\w+)@(\w+)"#)
println(email.matches("a@b"))
var firstMatch = email.find("mail x@y and p@q")
if (firstMatch != null) {
    println(firstMatch.value .. "@" .. firstMatch.start .. "-" .. firstMatch.end .. "#" .. firstMatch.groupCount)
    println(firstMatch.group(1) ?? "?")
}
println(email.findAll("a@b c@d").size)
println(email.replace("x@y z", "_"))

// -- 16. Exceptions (section 22) --------------------------------------------------------

// The `finally` clause runs on every exit path; clauses are tried in source order.
try {
    risky("parse")
}
catch (e: ValidationError) {
    println("validation: " .. e.getMessage())
}
finally {
    println("cleanup-a")
}

// A handler on a base type catches every subclass; `is` tests and a checked `as`
// cast recover the concrete exception type inside the handler body.
try {
    risky("escape")
}
catch (e: ApplicationException) {
    println("is scaled: " .. (e is ScaledError))
    var scaled = e as ScaledError
    println("base caught level " .. scaled.level)
    println("msg: " .. (e.getMessage() ?? "none"))
}

// Handlers may read typed fields, and a handled exception does not escape the frame.
func guarded(raw: String): String {
    try {
        return "ok:" .. risky(raw)
    }
    catch (e: ScaledError) {
        return "scaled level " .. e.level
    }
    catch (e: RuntimeException) {
        return "runtime"
    }
}
println(guarded("scale"))
println(guarded("fine"))
// A `finally` completion replaces whatever was in flight (section 22.3).
func replaced(): Integer {
    try {
        throw ValidationError("discarded")
    }
    finally {
        return 7
    }
}
println(replaced())
// A rethrown value continues outward after the `finally` of its own `try`.
try {
    try {
        throw ValidationError("inner")
    }
    catch (e: ValidationError) {
        println("rethrowing " .. e.getMessage())
        throw e
    }
    finally {
        println("cleanup-b")
    }
}
catch (e: RuntimeException) {
    println("outer caught " .. e.getMessage())
}

// -- 17. Unit and the last words ---------------------------------------------------------

var unitValue: Unit = {
    print("block-tail|")
}
println(unitValue)
print("no-newline ")
shoutLine("and a line")
// Included program pieces participate in the one program (section 20).
println(greet("World"))
println(math::double(21))
exit(0)
