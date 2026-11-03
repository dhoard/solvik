/*
 * example.sol — exercises every Solvik construct the compiler currently implements.
 *
 * Run it:
 *   ./solvik run example.sol
 * This transpiles to Java, compiles it, executes it, and caches the result under ~/.cache/solvik/.
 * It also includes include.sol and include-extra.sol, which declare modules in their own files.
 */

/*
 * Comments come in two forms, and block comments nest:
 *   /* this inner comment is still inside the outer one */
 *   still a comment
 */

// One writer for the whole transcript: every section writes through `out`.
out := solvik::io::TextWriter(solvik::io::Stdio.stdout())

/*
 * Literals
 */

mut count := 10  // Integer literal
price := 2.5  // Decimal literal
title := "widgets"  // String literal
enabled := true  // Boolean literal
absent := Null  // Null: the singleton type and value, written with a capital N
escaped := "quote: \" tab:\t newline:\n done"
escapedMore := "slash:\\ return:\r nul:\0"
raw := r#"  raw "text" keeps \ escapes and whitespace  "#
out.writeLine("more escapes: " .. escapedMore == "slash:\\ return:\r nul:\0" .. " "
    .. escapedMore.size())
intMin := -2147483648  // the unsuffixed Integer minimum is a literal, not a negation of 2147483648

/*
 * Declarations (§3.1, §3.2)
 */

// Every binding declares its type where it is created. `name := value` infers the exact static
// type of the value, `name: Type = value` checks it against a written type, and `mut` makes it
// assignable afterwards. `=` never declares, and `:=` never assigns.
mut counter := 0  // inferred Integer, mutable
counter = counter + 1  // assignment to the existing binding
mut declaredTotal: Integer = counter + 1  // explicitly typed Integer
name: String = "Solvik"  // an immutable explicitly typed binding
out.writeLine("declarations: " .. counter .. " " .. declaredTotal .. " " .. name)
// `name? := value` declares a binding whose type is the value's type plus `?`, so it may also
// hold `Null`. The name itself has no `?`: the marker belongs to the declaration.
mut countdownText? := "ready"  // type String?
countdownText = Null  // legal: String? accepts Null
countdownText = "go"  // legal: String is assignable to String?
out.writeLine("nullable inferred=" .. countdownText .. " " .. countdownText == Null)
// `name? := Null` infers Any?, because Null alone names no narrower non-null type.
mut anything? := Null  // type Any?
anything = "text"  // legal: String is assignable to Any?
anything = 42  // legal: Integer is assignable to Any
out.writeLine("nullable Any=" .. anything .. " " .. anything == Null)
// A nullable inference may also start at a present value; the type is the value's type plus `?`.
mut maybeNumber? := 1  // Integer?
maybeNumber = Null
out.writeLine("nullable number=" .. maybeNumber .. " " .. maybeNumber == Null)
// An explicitly typed declaration keeps a specific nullable type, and the type never changes.
mut label: String? = Null
label = "named"
out.writeLine("nullable explicit=" .. label)

/*
 * Conversions
 */

// Ten conversion constructors, one argument each: the numeric carriers, String, and Boolean.
// Numeric targets are range checked and abort with SOL-R006 when the value does not fit; a
// provable literal failure is the compile-time SOL-T008.
octet := Byte(100)  // Integer -> Byte, range checked
shortValue := Short(30000)  // Integer -> Short
fromText := Integer("42")  // String -> Integer
longValue := Long("2147483648")  // text -> Long, beyond Integer's literal range
floatValue := Float("2.5")  // String -> Float
doubleValue := Double(2.5)  // Decimal -> Double
bigInteger := BigInteger("123456789012345678901234567890")  // String -> BigInteger
asDecimal := BigDecimal(1)  // Integer -> Decimal
asText := String(123)  // any value -> String
asBoolean := Boolean("true")  // String -> Boolean
fromNumeric := BigInteger(42)  // numeric source -> BigInteger
decimalFromNumeric := BigDecimal(42)
longFromBig := Long(BigInteger("2147483648"))
booleanFromBoolean := Boolean(true)
falseFromText := Boolean("false")
textFromNull := String(Null)  // every value converts, including Null
out.writeLine("conversions: " .. octet .. " " .. shortValue .. " " .. fromText .. " "
    .. longValue .. " " .. floatValue .. " " .. doubleValue .. " " .. bigInteger .. " "
    .. asDecimal .. " " .. asText .. " " .. asBoolean)
out.writeLine("more conversions: " .. fromNumeric .. " " .. decimalFromNumeric .. " "
    .. longFromBig .. " " .. booleanFromBoolean .. " " .. falseFromText .. " "
    .. textFromNull .. " " .. Float(1))

// Float and Double also read the IEEE 754 special spellings; other text never becomes one.
out.writeLine("float text: " .. Float("NaN") .. " " .. Double("Infinity") .. " "
    .. Double("-Infinity") .. " " .. Float("+Infinity"))
// A fractional literal that overflows to Infinity or underflows to zero is SOL-T008; use a text
// conversion when those IEEE 754 values are intended.
// Rejected while compiling, because the text can never convert (SOL-T008):
//   Short(" 1")   Long("1_000")   Float("inf")   BigInteger("1.5")

/*
 * Numeric literal suffixes (§4)
 */

// An uppercase suffix is the literal form of the conversion constructor: `1L` is `Long(1)`.
// The suffix decides the type, so a suffixed literal never adopts an array's element type.
suffixedByte := 1B
suffixedShort := 1S
suffixedInteger := 1I
suffixedLong := 1L
suffixedFloat := 1.5F
integerSpelledFloat := 1F  // an integer-spelled literal may name any suffix
suffixedDouble := 1.5D
suffixedBigInteger := 123456789012345678901234567890BI
suffixedBigDecimal := 1.50BD
out.writeLine("suffix types: " .. suffixedByte.type() .. " " .. suffixedShort.type() .. " "
    .. suffixedInteger.type() .. " " .. suffixedLong.type() .. " " .. suffixedFloat.type() .. " "
    .. suffixedDouble.type() .. " " .. suffixedBigInteger.type() .. " "
    .. suffixedBigDecimal.type() .. " " .. integerSpelledFloat.type())
out.writeLine("suffix values: " .. suffixedByte .. " " .. suffixedShort .. " "
    .. suffixedInteger .. " " .. suffixedLong .. " " .. suffixedFloat .. " "
    .. suffixedDouble .. " " .. suffixedBigInteger .. " " .. suffixedBigDecimal)
out.writeLine("suffix = constructor: " .. 1L == Long(1) .. " "
    .. 1.50BD == BigDecimal("1.50") .. " " .. 1B == Byte(1))

// A sign belongs to a suffixed literal, so each carrier's minimum is writable.
out.writeLine("suffix minima: " .. -128B .. " " .. -32768S .. " " .. -2147483648I .. " "
    .. -9223372036854775808L)
// Suffixes take part in the ordinary promotion rules; Big carriers never mix with primitives.
out.writeLine("suffix arithmetic: " .. 1B + 1B .. " " .. 1L + 1 .. " " .. 1F + 1 .. " "
    .. 10L / 3L .. " " .. 1BD + 1BD .. " " .. 2BI.pow(3))
// An unsuffixed element adopts the array type; a suffixed one keeps its own (§4, §6).
suffixedBytes := Byte[2] {-128B, 127B}
adoptedLongs := Long[2] {1L, 2}
sizedWithSuffix := Integer[3I] {1, 2, 3}  // an array size may carry the I suffix
out.writeLine("suffix arrays: " .. suffixedBytes[0] .. " " .. suffixedBytes[1] .. " "
    .. adoptedLongs[1] .. " " .. sizedWithSuffix.size)
// Rejected while compiling:
//   Integer[1] {1L}          -> the suffix type wins, so this is SOL-T001
//   n := 1.5B             -> a fractional literal cannot be a Byte (SOL-T001)
//   n := 40000S           -> Short literal out of range (SOL-T008)
//   n := 1l               -> suffixes are uppercase (SOL-L007)
//   Integer[3L] {1, 2, 3}    -> an array size is an Integer literal (SOL-T001)
//   case 1L in an Integer switch -> a Long label cannot match the Integer subject

/*
 * Operators
 */

out.writeLine("precedence: " .. 1 + 2 * 3)  // 7
// `..` is the loosest operator, so a rendered value needs no parentheses: the arithmetic above is
// its operand. Grouping is required only when a concatenation is itself an operand.
out.writeLine("grouped concatenation: " .. ("id:" .. 1) == "id:1")  // true
out.writeLine("integer min: " .. intMin)
out.writeLine("overflow wraps: " .. 2147483647 + 1 .. " " .. 9223372036854775807L + 1L)
out.writeLine("widening: " .. 1 + 1.5)  // Decimal 2.5
out.writeLine("byte promote: " .. Byte(100) + Byte(100))  // Integer 200, no wrap at 127
out.writeLine("division: " .. 10 / 3 .. " remainder: " .. 10 % 3)  // truncates toward zero
out.writeLine("negative: " .. -7 / 2 .. " " .. -7 % 2)
out.writeLine("decimal divide: " .. 1.5 / 2)  // 0.75
out.writeLine("ieee division: " .. 1.0 / 0.0 .. " " .. -1.0 / 0.0 .. " " .. 0.0 / 0.0 .. " "
    .. 1.0 % 0.0)
out.writeLine("compare: " .. 1 < 2 .. " " .. 1 <= 1 .. " " .. 2 > 1 .. " " .. 2 >= 2 .. " "
    .. 1 < 1.5)
// Numeric comparison widens first.
out.writeLine("equality: " .. 1 == 1 .. " " .. 1 != 2 .. " " .. 1 == 1.0)
out.writeLine("decimal scale equality: " .. 1.0BD == 1.00BD .. " " .. 1.50BD == BigDecimal("1.50"))
out.writeLine("float rounding: " .. Float(16777216) == 16777217 .. " "
    .. Float("-0.0") == Float("0.0"))
out.writeLine("explicit equals: " .. 1.equals(1.0) .. " "
    .. Float("NaN").equals(Float("NaN")) .. " " .. Double("-0.0").equals(0.0) .. " "
    .. BigDecimal("1.0").equals(BigDecimal("1.00")))
out.writeLine("null equality: " .. Null == Null .. " " .. Null != 1 .. " " .. Null == 1)
out.writeLine("logical: " .. true && false .. " " .. true || false .. " " .. !false)
out.writeLine("concat: " .. "a" .. 1 .. "b" .. true)

// A value typed Any or Number compares with boxed equality, without dynamic promotion.
func boxedEqual(left: Any, right: Any) -> Boolean { return left == right }
func numberEqual(left: Number, right: Number) -> Boolean { return left == right }
out.writeLine("boxed equality: " .. boxedEqual(1, 1.0) .. " " .. boxedEqual(1, 1) .. " "
    .. numberEqual(1, 1.0) .. " " .. numberEqual(1, 1))

// `..=` appends to a String binding in place and requires `mut`; on an immutable binding it is
// SOL-T010. A declaration writes `mut` before the name, never after it.
mut log := "start"
log ..= "-middle"
log ..= "-end"
out.writeLine("append: " .. log)

// Identity is reference identity; value equality is `==`.
first := 1
sameValueRef := first
sameValue := 1
out.writeLine("identity: " .. first === sameValueRef .. " " .. first === sameValue)

/*
 * Statement separators
 */

one := 1; two := 2; out.writeLine("semicolons: " .. one + two)
banner := "continuation"

// a comment-only line still counts as blank, so this continuation is legal
\ .. " joined across lines"
out.writeLine(banner)

/*
 * Arrays
 */

bytes := Byte[4] {0, 1, 100, 127}  // literal elements take Byte
integers := Integer[3] {10, 20, 30}
decimals := BigDecimal[2] {1.5, 2.5}
flags := Boolean[2] {true, false}
words := String[2] {"Hello", "World"}
mixed := Any[3] {1, "one", true}  // children of Any
numbers := Number[2] {Byte(1), 2.5}  // children of Number
out.writeLine("sizes: " .. bytes.size .. " " .. integers.size .. " " .. decimals.size)
out.writeLine("indexing: " .. bytes[0] .. " " .. bytes[3] .. " " .. words[1] .. " " .. integers[2])
out.writeLine("array type: " .. integers.type())  // every array reports Array
// An array's length is its only member: an immutable Integer that reads the length the array was
// constructed with (§5). It is a member, not a function, and it never changes.
fixedBytes := Byte[10] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9}
out.writeLine("array member: " .. fixedBytes.size)  // 10
slots := Byte?[10] {}
out.writeLine("member of empty slots: " .. slots.size)  // 10
out.writeLine("member in expressions: " .. integers.size .. " items" .. " "
    .. integers.size == 3 .. " " .. integers.size + 1)  // 3 items true 4
// Rejected while compiling:
//   integers.size()        -> the member is not a function (SOL-T001)
//   integers.size = 4      -> the member is immutable (SOL-T010)
// The index carrier may be Integer, Byte, or Short; all three are range checked at run time.
shortIndex := Short(1)
byteIndex := Byte(2)
out.writeLine("carrier indices: " .. integers[shortIndex] .. " " .. integers[byteIndex])
mut total := 0
for value in integers {
    total = total + value
}
out.writeLine("for total: " .. total)  // 60
for item in mixed {
    // Any-typed elements keep their dynamic type.
    out.write(item.type() .. "=" .. item .. " ")
}
out.writeLine("")

// An element is assigned through the same `[index]` form. The size never changes:
// there is no resize, append, or removal in the language.
counts := Integer[3] {0, 0, 0}
counts[0] = 7
slotIndex := 1
counts[slotIndex] = counts[0] + 1
out.writeLine("element write: " .. counts[0] .. " " .. counts[1] .. " " .. counts[2])
squares := Integer[4] {0, 0, 0, 0}
mut squareIndex := 0
while squareIndex < squares.size {
    squares[squareIndex] = (squareIndex + 1) * (squareIndex + 1)
    squareIndex = squareIndex + 1
}
out.writeLine("filled in place: " .. squares[0] .. " " .. squares[1] .. " " .. squares[3])
words[0] ..= "!"  // append to an element, not to the array
out.writeLine("element append: " .. words[0])
grid := Integer[2][2] {Integer[2] {1, 2}, Integer[2] {3, 4}}
grid[1][0] = 9  // nested elements are reached the same way
out.writeLine("nested write: " .. grid[0][0] .. " " .. grid[1][0])

// `Integer[][]` is how a nested array is written in a signature.
func flatten(rows: Integer[][]) -> Integer {
    mut sum := 0
    for row in rows {
        for cell in row {
            sum = sum + cell
        }
    }
    return sum
}
out.writeLine("nested signature: " .. flatten(grid))

// The written length is part of the array type, so `String[4]` and `String[3]` differ. A
// non-nullable element type must list exactly N elements; a nullable one may list fewer, because
// the construction rule makes the omitted elements Null (§5.1).
roster := String?[4] {"ann", "bob"}
out.writeLine("partial literal: " .. roster.size .. " " .. roster)
roster[3] = "dee"
pending := Integer?[3] { }
pending[0] = 5
out.writeLine("filled later: " .. roster[3] .. " " .. pending.size .. " " .. pending)

// An array type names its length: `Integer[3]` and `Integer[2]` are different fixed-length types,
// and a signature may require one of them. `Integer[]` is the size-erased array type: it accepts
// any length and is what `split()` returns.
func third(values: Integer[3]) -> Integer {
    return values[2]
}
out.writeLine("fixed length: " .. third(Integer[3] {7, 8, 9}))
mut nullableFixed: Integer[3]? = Integer[3] {1, 2, 3}  // the array may be Null, elements may not
nullableFixed = Null
out.writeLine("nullable fixed array: " .. nullableFixed .. " " .. nullableFixed == Null)
// Rejected while compiling:
//   third(Integer[2] {1, 2})       an Integer[2] is not an Integer[3] (SOL-T001)
//   Integer[2] {1}                 one non-null element is missing (SOL-T001)
//   Integer[2] {1, Null}           Null is not an Integer element (SOL-T001)
//   Integer?[2] {1, 2, 3}          more elements than the length allows (SOL-T001)
// Element nullability is invariant, so one array is never reachable under two element types:
//   mut plain: Integer[2] = Integer[2] {1, 2}
//   mut holes: Integer?[2] = plain          an Integer[2] is not an Integer?[2] (SOL-T001)

// A parameter may take the parent type of its arguments: `Number` accepts Byte, Integer,
// and Decimal, and `Any` accepts everything. Conversions stay exact, so a Number is not
// converted; it is rendered or asked for its type instead.
func describeAll(numbers: Number[]) -> String {
    mut text := ""
    for number in numbers {
        text = text .. number.type() .. "=" .. number .. " "
    }
    return text
}
out.writeLine("Number parameter: " .. describeAll(Number[3] {Byte(2), 40, 0.5}))

// Invalid lengths and element counts are compile errors, not truncation:
//   Integer[0] { }   Integer[2147483648] { }   Integer[1] {1, 2}   Integer[2] {1}
//   Integer[2] {1, Null}   Integer[3] = Integer[2] {1, 2}   (SOL-T001 each)
// An index literal outside int is also a compile error; an out-of-range runtime index is SOL-R004.

/*
 * Control flow
 */

// A condition must be Boolean; there is no truthiness, so `if 1` is SOL-T001.
mut countdown := 3
while countdown > 0 {
    out.write(countdown .. ".. ")
    countdown = countdown - 1
}
out.writeLine("liftoff")

// `continue` skips an iteration and `break` leaves the loop; both target the
// innermost loop, and a `switch` between them changes nothing.
mut ticks := 0
while true {
    ticks = ticks + 1
    if ticks == 2 {
        continue  // skip printing 2
    }
    if ticks > 4 {
        break  // the body decides when to stop
    }
    out.writeLine("tick " .. ticks)
}
stops := Integer[5] {1, 2, 3, 4, 5}
mut kept := 0
for stop in stops {
    switch stop.type() {
        case "Integer" {
            if stop == 2 {
                continue
            }
            if stop > 4 {
                break  // leaves the for loop, not the switch
            }
            kept = kept + stop
        }
        default {
            out.writeLine("unreachable")
        }
    }
}
out.writeLine("kept " .. kept)

/*
 * Scope and shadowing
 */

// A block is a scope, and a nested declaration shadows the enclosing one.
mut shadowed := 1
{
    mut shadowed := outer shadowed + 1  // 2
    out.writeLine("shadow: inner " .. shadowed)
    {
        mut shadowed := outer shadowed * 10  // 20: the middle binding
        out.writeLine("shadow: nested " .. shadowed)
    }
    outer shadowed = 5  // writes through to the middle binding
    out.writeLine("shadow: after write-through " .. shadowed)
}
out.writeLine("shadow: inner " .. shadowed)  // the outermost binding is untouched

// A shadow may take a different type: a binding keeps its declared type forever (§3).
typed := 1
{
    typed := "now a String"
    out.writeLine("shadow: " .. typed .. " " .. typed.type())
}
out.writeLine("shadow: " .. typed .. " " .. typed.type())

// `outer` reaches a parameter, a for binding, and an enclosing declaration.

func shadowing(item: Integer) -> String {
    {
        item := "shadow"
        return item .. " over " .. outer item
    }
}
out.writeLine("outer param: " .. shadowing(3))

// `outer` assigns and appends exactly like the plain name.
mut trail := "a"
{
    mut trail := "b"
    outer trail ..= "c"
    out.writeLine("outer append: " .. trail)
}
out.writeLine("outer append: " .. trail)
for flag2 in flags {
    flag2 := !flag2
    out.writeLine("outer loop binding: " .. flag2 .. " was " .. outer flag2)
}
mut shadowSlot: Integer? = Null
shadowSlot = 7
{
    mut shadowSlot := 99
    out.writeLine("outer shadowing: " .. shadowSlot .. " " .. outer shadowSlot)
}

/*
 * Switch
 */

// Only String, Integer, Byte, and Boolean subjects exist. Short, Long, and big-number subjects are
// SOL-T001, and a label must have the subject's type: `case 1L` cannot match an Integer subject.
// Literal arms must precede regex arms, and an invalid regex is SOL-T006.
method := "POST"
switch method {
    case "GET" {
        out.writeLine("switch String: read")
    }
    case "POST" {
        out.writeLine("switch String: write")
    }
    case "DELETE" {
        out.writeLine("switch String: remove")  // skipped: only one arm ever runs
    }
    default {
        out.writeLine("switch String: other")
    }
}

// Parentheses around the subject are optional and change nothing.
switch (method) {
    case "PUT" {
        out.writeLine("switch parens: put")
    }
    default {
        out.writeLine("switch parens: not a PUT")
    }
}

// A switch with no default and no matching arm does nothing at all.
switch method {
    case "PATCH" {
        out.writeLine("switch no-default: patch")
    }
}

// Regex arms match the whole subject and are tried in source order, after every
// literal arm. Their patterns are compiled once, not once per execution.

func route(path: String) -> String {
    switch path {
        case "/health" {
            return "literal"
        }
        case regex "^/api/users/[0-9]+$" {
            return "user"
        }
        case regex "^/api/.*" {
            return "api"
        }
        default {
            return "page"
        }
    }
}
out.writeLine("switch regex: " .. route("/health") .. " " .. route("/api/users/7") .. " "
    .. route("/api/status") .. " " .. route("/about"))

// Integer, Byte, and Boolean subjects all dispatch too.

func name(code: Integer) -> String {
    switch code {
        case -1 {
            return "minus one"
        }
        case 0 {
            return "zero"
        }
        case 200 {
            return "two hundred"
        }
        default {
            return "other"
        }
    }
}
out.writeLine("switch Integer: " .. name(-1) .. " " .. name(0) .. " " .. name(200) .. " "
    .. name(7))
for byteValue in bytes {
    switch byteValue {
        case 0B {
            out.writeLine("switch Byte: zero")
        }
        case 127B {
            out.writeLine("switch Byte: max")
        }
        default {
            out.writeLine("switch Byte: " .. byteValue)
        }
    }
}
for flag in flags {
    switch flag {
        case true {
            out.writeLine("switch Boolean: true")
        }
        case false {
            out.writeLine("switch Boolean: false")
        }
    }
}

// Switches nest, and one inside a loop runs its arms once per iteration.
kinds := String[3] {"get", "post", "trace"}
for kind in kinds {
    switch kind {
        case "get" {
            switch method {
                case "POST" {
                    out.writeLine("nested: get is sent as POST")
                }
            }
        }
        default {
            out.writeLine("nested: " .. kind)
        }
    }
}

/*
 * String methods (§7)
 */

padded := "  Hello, World  "
out.writeLine("string size: " .. padded.size())
out.writeLine("string strip: [" .. padded.strip() .. "] [" .. padded.stripLeading() .. "] ["
    .. padded.stripTrailing() .. "]")
out.writeLine("string case: " .. padded.strip().toUpperCase() .. " "
    .. padded.strip().toLowerCase())
out.writeLine("string substring: " .. padded.strip().substring(0, 5) .. " "
    .. padded.strip().substring(7))
out.writeLine("string search: " .. padded.indexOf("World") .. " " .. padded.indexOf("absent") .. " "
    .. padded.contains("World") .. " " .. padded.strip().startsWith("Hello") .. " "
    .. padded.strip().endsWith("d"))
out.writeLine("string replace: " .. padded.strip().replace("World", "Solvik"))

// split is literal, keeps empty parts, and returns an ordinary array.
csv := "alpha,beta,,delta,"
fields := csv.split(",")
out.writeLine("split: " .. fields.size .. " " .. fields[0] .. "|" .. fields[1] .. "|"
    .. fields[2] .. "|" .. fields[3] .. "|" .. fields[4] .. "end")
for field in fields {
    out.write("[" .. field .. "]")
}
out.writeLine("")
fields[2] = "gamma"
out.writeLine("split array write: " .. fields[2])

// String methods compose with everything else: arrays, switch, functions.

func pathKind(path: String) -> String {
    switch path.substring(0, 5) {
        case regex "^/api/" {
            return "api"
        }
        default {
            return "page"
        }
    }
}
out.writeLine("string + switch: " .. pathKind("/api/users") .. " " .. pathKind("/index.html"))

// These fail at run time only when the index or separator is not a literal the compiler can
// reject (SOL-R009 / SOL-R010); a literal empty separator is the compile-time SOL-T008 case:
//   index := 9
//   padded.substring(index)
//   separator := ""
//   padded.split(separator)

/*
 * Identity, hashing, and conversion text
 */

// `===` is value identity for scalars: no cache size can change the answer.
out.writeLine("identity scalars: " .. Byte(7) === Byte(7) .. " " .. Byte(100) === Byte(100) .. " "
    .. 1 === 1.0 .. " " .. "a" === "a" .. " " .. true === true)

// ...and reference identity for arrays and user types, as `==` also is for them.
leftArray := Integer[1] {1}
rightArray := Integer[1] {1}
out.writeLine("identity references: " .. leftArray === leftArray .. " "
    .. leftArray === rightArray .. " " .. leftArray != rightArray)
out.writeLine("array equals method: " .. leftArray.equals(leftArray) .. " "
    .. leftArray.equals(rightArray))

// Equal values hash alike whatever their carrier or scale.
out.writeLine("hash agrees: " .. 1 == 1.0 .. " " .. 1.hashCode() == 1.0.hashCode() .. " "
    .. Byte(1).hashCode() == 1.hashCode() .. " " .. 1.50.hashCode() == 1.5.hashCode())

// Conversions read text exactly as written: no trimming, ASCII digits, one dot.
out.writeLine("conversion text: " .. Integer("+42") .. " " .. Integer("0042") .. " "
    .. BigDecimal("1e3") .. " " .. BigDecimal(".5") .. " " .. Boolean("true"))

// Rejected while compiling, because the literal can never convert (SOL-T008):
//   Integer(" 42")   Integer("1_000")   BigDecimal("5.")   Boolean("TRUE")
// A Decimal renders in plain form, keeping the scale it was written with.
out.writeLine("decimal render: " .. BigDecimal("1e3") .. " " .. BigDecimal("1e-3") .. " "
    .. 1.50 .. " " .. 0.1 + 0.2)

/*
 * Big-number Java methods (§4)
 */

// BigInteger and BigDecimal are stored as the Java classes, so their public instance methods
// whose parameter and return types map to Solvik types are callable directly. There is no
// adapter and no reflection: the compiler resolves the overload at compile time.
twelve := BigInteger("12")
eighteen := BigInteger("18")
out.writeLine("big integer methods: " .. twelve.gcd(eighteen) .. " " .. twelve.pow(2) .. " "
    .. twelve.bitLength() .. " " .. twelve.toString(2) .. " " .. twelve.add(eighteen))

scaled := BigDecimal("1234.5000")
out.writeLine("big decimal methods: " .. scaled.scale() .. " " .. scaled.stripTrailingZeros() .. " "
    .. scaled.unscaledValue() .. " " .. scaled.movePointLeft(2) .. " "
    .. scaled.add(BigDecimal("0.5")))
out.writeLine("big suffix methods: " .. 12BI.gcd(18BI) .. " " .. 1.50BD.scale() .. " "
    .. 2BD.add(1BD))
out.writeLine("big integer arithmetic: " .. 12BI - 2BI .. " " .. 12BI * 2BI .. " "
    .. 12BI / 5BI .. " " .. 12BI % 5BI)
out.writeLine("big decimal arithmetic: " .. 1.5BD + 2.25BD .. " " .. 1.5BD - 0.5BD .. " "
    .. 1.5BD * 2BD .. " " .. 10BD / 4BD)

// No Solvik type can represent a byte[] result, and an unknown name is SOL-T001:
//   twelve.toByteArray()      twelve.noSuchMethod()
// Java static members of the big-number classes (BigInteger.valueOf, BigDecimal.ZERO) are not
// reachable through a value; declare a native type for static Java methods. Big numbers never mix
// with primitives in an operator, so 1 + 1BI is SOL-T001.
// Equally good overloads are SOL-T014, and % is not defined for BigDecimal:
//   BigDecimal("1.5") % BigDecimal("1.5")
// Division and remainder by zero abort for integral and big-number carriers (SOL-R001/R002), and
// a BigDecimal division that cannot terminate aborts as SOL-R003.

/*
 * Null values and nullability (§2)
 */

// `Null` is a first-class singleton type and value. Only `T?` accepts it, so an ordinary type
// never holds it: `mut count: Integer = 1` followed by `count = Null` is SOL-T001. A nullable
// binding keeps its static type, so it still prints and compares once it holds Null.
mut nilCount: Integer? = 1
nilCount = Null
out.writeLine("null Integer: " .. nilCount .. " " .. nilCount == Null .. " " .. nilCount != 1)
nilCount = 2
out.writeLine("restored Integer: " .. nilCount)
mut nilByte: Byte? = Byte(9)
nilByte = Null
out.writeLine("null Byte: " .. nilByte)
mut nilFlag: Boolean? = true
nilFlag = Null
out.writeLine("null Boolean: " .. nilFlag)
mut nilDecimal: Double? = 1.5
nilDecimal = Null
out.writeLine("null Double: " .. nilDecimal)
mut nilText: String? = "text"
nilText = Null
out.writeLine("null String: " .. nilText .. " " .. nilText == Null)
nilText = "text again"
out.writeLine("restored String: " .. nilText)
mut nilArray: Integer[2]? = Integer[2] {1, 2}  // a nullable array with non-null elements
nilArray = Null
out.writeLine("null array: " .. nilArray .. " " .. nilArray == Null)

// The singleton value's own type is Null, and equality and String(value) accept it.
nothing := Null
out.writeLine("null value: " .. nothing .. " " .. nothing == Null .. " " .. Null === Null .. " "
    .. "String(Null)=" .. String(Null))
// `Null` is a value, so `Null()` is SOL-T001: it is not a constructor and has no members.
// Null stops at the operations defined for it: it is accepted by ==, !=, ===, and String(value),
// and a nullable value must be proven present before anything else (§7.1).

// A null check narrows a binding to its non-null type inside the branch.
func describeOptional(text: String?) -> String {
    if text == Null {
        return "absent"
    }
    return text.size() .. ":" .. text  // narrowed: text is a String here
}
out.writeLine("null narrowing: " .. describeOptional(Null) .. " " .. describeOptional("four"))

// `Null` is also a written type: a function that can only return the singleton value declares
// `-> Null`, which is not the same as a function with no return value.
func absentValue() -> Null {
    return Null
}
out.writeLine("null return: " .. absentValue() .. " " .. absentValue() == Null)

// The array and its elements carry separate nullability (§5): `Integer?[2]` is a non-null array
// whose elements may be Null, and `Integer[2]?` is a nullable array of non-null elements.
mut elementHoles: Integer?[3] = Integer?[3] {1, Null, 3}
out.writeLine("element null: " .. elementHoles[1] .. " " .. elementHoles[1] == Null .. " "
    .. elementHoles[2])
elementHoles[1] = 2
out.writeLine("element filled: " .. elementHoles[1])

/*
 * Functions
 */

func factorial(n: Integer) -> Integer {
    if n <= 1 {
        return 1
    }
    return n * factorial(n - 1)  // recursion, and a forward reference below
}

func describe(value: Any) -> String {
    return value.type() .. ":" .. value
}

func banner2() {  // no return type: callable only as a statement
    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine("void function ran")
}
out.writeLine("recursion: " .. factorial(6))  // 720
out.writeLine("describe: " .. describe(42) .. " " .. describe("text") .. " " .. describe(true))
out.writeLine("forward reference: " .. later(3))  // declared below the call site
banner2()

func later(n: Integer) -> String {
    return "later(" .. n .. ")"
}

// Parameter lists, argument lists, and type bodies are newline-insensitive.
func multiline(
    first: Integer,
    second: Integer,
    label: String
) -> String {
    return label .. ":" .. first + second
}
out.writeLine("multiline: " .. multiline(
    1,
    2,
    "sum"
))
// Every delimited construct may span lines, not just argument lists: a newline inside `(...)`,
// `[...]`, or a type-argument list is layout, and only a newline between statements ends one.
grouped := (
    1 +
    2
)
picked := Integer[3] {10, 20, 30}[
    2
]
spannedLists := solvik::collection::List<
    String
>()
spannedLists.add("spanned")
spannedSized := Integer[
3
] {1, 2, 3}
out.writeLine("delimited lines: " .. grouped .. " " .. picked .. " " .. spannedLists.get(0) .. " "
    .. spannedSized.size)

// Output arguments are evaluated once, left to right, before anything renders.
func nextCount(state: Integer[]) -> Integer {
    state[0] = state[0] + 1
    return state[0]
}
orderState := Integer[1] {10}
out.writeLine("evaluation order: " .. nextCount(orderState) .. " " .. nextCount(orderState) .. " "
    .. nextCount(orderState) .. " state=" .. orderState[0])

/*
 * The Any contract methods
 */

sample := 255
out.writeLine("toString: " .. sample.toString())
out.writeLine("type: " .. sample.type())
out.writeLine("equals: " .. sample.equals(255) .. " " .. sample.equals(1))
out.writeLine("hash: " .. sample.hashCode())
out.writeLine("size of array: " .. bytes.size)

/*
 * Branching
 */

if total > 100 {
    out.writeLine("branch: large")
} else if total > 50 {
    out.writeLine("branch: medium")
} else {
    out.writeLine("branch: small")
}
for value in integers {
    if value == 20 {
        out.writeLine("branch: found 20")
    }
}

/*
 * Closing
 */

out.writeLine("done: " .. count .. " " .. price .. " " .. title .. " " .. enabled)
out.writeLine("null renders as: " .. Null)  // Null renders as null (§4)
out.write("")  // writes no bytes
out.writeLine("")  // writes exactly one terminator
out.writeLine("empty output calls: done")
// A text write has no result and exit has no result; each is callable only as a statement.

/*
 * Standard I/O (§11.3)
 */

// The bundled I/O types are ordinary Solvik types declared in packaged `.sol` sources:
// solvik::io::Channel, solvik::io::Stdio, solvik::io::TextReader, solvik::io::TextWriter,
// solvik::io::ByteReader, solvik::io::ByteWriter, solvik::io::File, and solvik::io::IoError.
// A program names them through the `solvik::io` module and does not include them; `out` at the
// top of this file is `TextWriter(Stdio.stdout())`.
out.write("a write adds no terminator")
out.writeLine("")
out.flush()
out.writeLine("line ending is [" .. solvik::io::TextWriter.EOL .. "]")
// solvik::io::TextWriter.EOL is a static immutable String: read it through the type,
// never through a writer. `out.EOL` is SOL-T001 and assigning `TextWriter.EOL` is SOL-T010.

// A solvik::io::Channel is an opaque byte endpoint. solvik::io::Channel.bytes() is a duplex
// in-memory channel: writes append, reads consume from the front, and end of input is Null
// rather than an empty array.
memory := solvik::io::Channel.bytes()
out.writeLine("memory: open=" .. memory.isOpen() .. " read=" .. memory.canRead() .. " write="
    .. memory.canWrite())
seed := solvik::io::TextWriter(memory)
seed.write("first\r\nsecond\nthird\rfourth")
seed.close()

readChunk := memory.read(5)  // "first": at most five bytes
if readChunk != Null {
    out.writeLine("read " .. readChunk.size .. " bytes: " .. readChunk)
}
reader := solvik::io::TextReader(memory)
mut lineNumber := 0
mut line: String? = reader.readLine()
while line != Null {
    lineNumber = lineNumber + 1
    out.writeLine("line " .. lineNumber .. ": [" .. line .. "]")
    line = reader.readLine()
}
out.writeLine("lines: " .. lineNumber)
reader.close()  // closes the reader, never the channel

// `write` reports the bytes it accepted; `writeAll` repeats it until every byte is sent.
sink := solvik::io::Channel.bytes()
payload := Byte[4] {0, 127, -128, -1}  // 0x00, 0x7F, 0x80, 0xFF survive the round trip
out.writeLine("write accepted " .. sink.write(payload) .. " bytes")
sink.writeAll(Byte[2] {65, 66})
sink.flush()
roundTrip := sink.read(6)
if roundTrip != Null {
    out.writeLine("round trip: " .. roundTrip.size .. " bytes, first=" .. roundTrip[0] .. ", last="
        .. roundTrip[5])
}

textSink := solvik::io::Channel.bytes()
echoWriter := solvik::io::TextWriter(textSink)
echoWriter.writeLine("echoed")
echoWriter.close()
echo := solvik::io::TextReader(textSink)
out.writeLine("echo: " .. echo.readLine() .. " eof=" .. echo.readLine() == Null)
echo.close()

// solvik::io::Stdio setters replace one current channel binding. A writer keeps the channel it
// was built with, and a saved standard reference still targets the original endpoint.
savedOut := solvik::io::Stdio.stdout()
captured := solvik::io::Channel.bytes()
solvik::io::Stdio.setStdout(captured)
solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine("captured to memory")
solvik::io::Stdio.setStdout(savedOut)
captureReader := solvik::io::TextReader(captured)
out.writeLine("captured: " .. captureReader.readLine())
captureReader.close()

// Standard input and standard error are the same kind of binding. Replacing stdin with a
// readable channel exercises the reader path without depending on the process's own input.
savedIn := solvik::io::Stdio.stdin()
out.writeLine("stdin readable=" .. savedIn.canRead() .. " writable=" .. savedIn.canWrite())
memoryIn := solvik::io::Channel.bytes()
seedIn := solvik::io::TextWriter(memoryIn)
seedIn.write("line from memory stdin\n")
seedIn.close()
solvik::io::Stdio.setStdin(memoryIn)
fromStdin := solvik::io::TextReader(solvik::io::Stdio.stdin())
out.writeLine("stdin: " .. fromStdin.readLine())
fromStdin.close()
solvik::io::Stdio.setStdin(savedIn)

savedErr := solvik::io::Stdio.stderr()
capturedErr := solvik::io::Channel.bytes()
solvik::io::Stdio.setStderr(capturedErr)
solvik::io::TextWriter(solvik::io::Stdio.stderr()).writeLine("captured stderr")
solvik::io::Stdio.setStderr(savedErr)
errReader := solvik::io::TextReader(capturedErr)
out.writeLine("stderr: " .. errReader.readLine())
errReader.close()

/*
 * File and byte I/O (§11.3)
 */

// solvik::io::File owns one single-direction byte channel; solvik::io::ByteReader and
// solvik::io::ByteWriter borrow it. The path is unique to this run and is deleted at the end,
// so the corpus leaves no file behind.
filePath := "solvik-example-" .. solvik::util::UUID.now().toString() .. ".bin"
file := solvik::io::File.openWrite(filePath)
out.writeLine("file: path=" .. file.path() == filePath .. " open=" .. file.isOpen() .. " canWrite="
    .. file.channel().canWrite())
bytesOut := solvik::io::ByteWriter(file.channel())
bytesOut.write(Byte[4] {0, 127, -128, -1})
bytesOut.flush()
bytesOut.close()
file.close()

appended := solvik::io::File.openAppend(filePath)
solvik::io::ByteWriter(appended.channel()).write(Byte[2] {65, 66})
appended.close()

readFile := solvik::io::File.openRead(filePath)
out.writeLine("file: canRead=" .. readFile.channel().canRead() .. " canWrite="
    .. readFile.channel().canWrite())
bytesIn := solvik::io::ByteReader(readFile.channel())
head := bytesIn.read(4)
tail := bytesIn.read(2)
if head != Null && tail != Null {
    out.writeLine("file bytes: " .. head .. " then " .. tail .. " eof=" .. bytesIn.read(1) == Null)
}
bytesIn.close()
out.writeLine("file: reader closed, channel open=" .. readFile.channel().isOpen())
readFile.close()

// A closed solvik::io::File and a closed wrapper raise the published codes.
try {
    closedFile := solvik::io::File.openRead(filePath)
    closedFile.close()
    ignored := closedFile.channel()
} catch (error: Exception) {
    out.writeLine("file closed: " .. error.getCode() == solvik::io::IoError.Closed)
}
try {
    bytesGone := solvik::io::ByteReader(solvik::io::Channel.bytes())
    bytesGone.close()
    ignored := bytesGone.read(1)
} catch (error: Exception) {
    out.writeLine("byte reader closed: " .. error.getCode() == solvik::io::IoError.Closed)
}
try {
    missingFile := solvik::io::File.openRead(filePath .. ".absent")
} catch (error: Exception) {
    out.writeLine("file missing: " .. error.getCode() == solvik::io::IoError.Io)
}

out.writeLine("file exists: " .. solvik::io::File.exists(filePath))
out.writeLine("file deleted: " .. solvik::io::File.delete(filePath) .. " gone="
    .. solvik::io::File.exists(filePath) == false)

// I/O failures are ordinary Solvik exceptions carrying a published solvik::io::IoError code.
try {
    memory.close()
    memory.close()  // repeated close is harmless
    ignored := memory.read(1)  // a closed channel raises solvik::io::IoError.Closed
} catch (error: Exception) {
    out.writeLine("closed: " .. error.getCode() == solvik::io::IoError.Closed .. " "
        .. error.getMessage())
}
try {
    solvik::io::Stdio.stdout().close()  // the original standards are protected
} catch (error: Exception) {
    out.writeLine("protected: " .. error.getCode() == solvik::io::IoError.Unsupported)
}
malformed := solvik::io::Channel.bytes()
rejected := malformed.write(Byte[1] {-1B})  // 0xFF is not valid UTF-8
badReader := solvik::io::TextReader(malformed)
try {
    invalid := badReader.readLine()
} catch (error: Exception) {
    out.writeLine("encoding error: " .. error.getCode() == solvik::io::IoError.Encoding .. " after "
        .. rejected .. " byte")
}
badReader.close()

/*
 * Static initializers (§3.4)
 */

// A type has at most one static block, written after its fields and before its functions. It runs
// once, after those field initializers, when the type is first actively used. A type-only mention
// (the nullable declaration below) is not a use.
type StaticLog {
    static pub mut text := ""
}

type StaticLazy {
    static {
        StaticLog.text = StaticLog.text .. "lazy"
    }
}

type StaticOrdered {
    static mut trace := "field"
    static pub first := trace
    static {
        trace = trace .. "-block"
    }
    static pub func state() -> String {
        return trace .. ":" .. first
    }
}

out.writeLine("static before=" .. StaticLog.text)
staticUnused: StaticLazy? = Null
out.writeLine("static mention=" .. StaticLog.text)
out.writeLine("static ordered=" .. StaticOrdered.state())
out.writeLine("static ordered again=" .. StaticOrdered.state())

// The block may construct the enclosing type through its private constructor.
type StaticConfig {
    static mut instance: StaticConfig? = Null
    pub mut name := ""
    static {
        instance = StaticConfig("configured")
    }
    func StaticConfig(name: String) {
        self.name = name
    }
    static pub func current() -> StaticConfig {
        value := instance
        if value == Null {
            raise Exception(1, "static config was not initialized")
        }
        return value
    }
}
out.writeLine("static singleton=" .. StaticConfig.current().name)

/*
 * User-defined types
 */

// Members are private and immutable unless they say otherwise:
//   `pub`      — readable from outside the type
//   `mut`      — writable after construction (inside the type)
//   `pub mut`  — readable and writable from outside

type Point {

    pub mut x := 0
    pub mut y := 0
    mut labels := 0

    pub func Point(x: Integer, y: Integer) {
        self.x = x  // `self.` because the parameters are also named x and y
        self.y = y
        labels = 0  // a plain member name: no local shadows it
    }

    pub func shift(dx: Integer, dy: Integer) {
        x = x + dx  // `mut` allows this after construction
        y = y + dy
        labels = labels + 1
    }

    pub func labelCount() -> Integer {
        return labels  // a private member, read through a method
    }

    pub func toString() -> String {
        return "(" .. x .. ", " .. y .. ")"
    }
}
origin := Point(0, 0)
corner := Point(3, 4)
mut missing: Point? = Point(0, 0)
missing = Null
out.writeLine("null user type: " .. missing .. " " .. missing == Null)
missing = Point(1, 1)
out.writeLine("restored user type: " .. missing)
corner.shift(1, -1)
out.writeLine("types: " .. origin .. " " .. corner .. " " .. corner.type())
out.writeLine("pub read: " .. corner.x .. " " .. corner.y .. " " .. corner.labelCount())
corner.x = 10  // `pub mut`: writable from outside
out.writeLine("pub write: " .. corner.x)

// `size` is the array's member, not a reserved name: a type may still declare its own function of
// that name, and calling it stays a call. The array field is read with the member.
type Ledger {
    pub items := Integer[2] {1, 2}

    pub func Ledger() { }

    pub func size() -> Integer {
        return self.items.size
    }
}
out.writeLine("user size(): " .. Ledger().size() .. " " .. Ledger().items.size)

// These are compile errors, by design:
//   corner.labels            -> private member
//   corner.labels = 1        -> private member
// A `pub mut` field stays writable after construction: `corner.x = 1` is legal.
// Types named like the generated classes are allowed: generated names contain '$'.

// An array may hold user values, and its elements keep their declared type.
points := Point[2] {Point(1, 1), Point(2, 2)}
out.writeLine("user array: " .. points[0] .. " " .. points[1] .. " " .. points[1].type())

// Overloaded constructors and methods share a name when their parameter types differ. A user
// `equals(other: Any)` sees a checked peer of its own type, so private fields and methods of the
// argument are reachable; operator `==` still uses reference identity.
type Version {

    major := 0
    minor := 0

    pub func Version(major: Integer) {
        self.major = major
    }

    pub func Version(major: Integer, minor: Integer) {
        self.major = major
        self.minor = minor
    }

    func sameAs(other: Version) -> Boolean {
        return major == other.major && minor == other.minor
    }

    pub func equals(other: Any?) -> Boolean {
        return sameAs(other)  // other is the checked peer: narrowed to Version here
    }

    pub func hashCode() -> Integer {
        return major * 100 + minor
    }

    pub func toString() -> String {
        return major .. "." .. minor
    }

    pub func label(value: Integer) -> String { return "int:" .. value }
    pub func label(value: String) -> String { return "str:" .. value }
}
v1 := Version(1, 2)
v2 := Version(1, 2)
v3 := Version(2)
out.writeLine("user equals: " .. v1 == v2 .. " " .. v1.equals(v2) .. " " .. v1.equals(v3) .. " "
    .. v1.equals(Null) .. " " .. v1.equals("not a version"))
out.writeLine("user hash: " .. v1.hashCode() .. " " .. v2.hashCode() .. " "
    .. v1.equals(v2) && v1.hashCode() == v2.hashCode())
out.writeLine("user overloads: " .. v3 .. " " .. v1.label(7) .. " " .. v1.label("seven"))
// Contract overrides must be public and exact: equals(other: Any) -> Boolean,
// hashCode() -> Integer, and toString() -> String. `type()` is final and cannot be overridden.
versionOwners := solvik::collection::Map<Version, String>()
versionOwners.put(v1, "ann")
out.writeLine("user key: " .. versionOwners.get(v2) .. " " .. versionOwners.size())
// These are compile errors, by design:
//   v1.sameAs(v2)            -> a private method
//   v1.major                 -> a private field
//   v1.major = 3             -> a private field (T001)
//   Version()                -> there is no implicit constructor

// A private constructor plus a static factory keeps construction inside the type.
type Token {

    value := ""

    func Token(value: String) {
        self.value = value
    }

    static pub func of(value: String) -> Token {
        return Token(value)
    }

    pub func text() -> String {
        return value
    }
}
out.writeLine("private constructor: " .. Token.of("made").text())

type Solvik {

    mut note := "language"

    pub func Solvik() {
        note = "language"
    }

    pub func describe() -> String {
        switch note {
            case regex "^lang.*" {
                return "Solvik(language)"
            }
            default {
                return "Solvik(" .. note .. ")"
            }
        }
    }
}
out.writeLine(Solvik().describe())

/*
 * Static type members
 */

// `static` declares a field or function that belongs to the type, not to an instance. It is
// valid only on type members (never on a type declaration), may be combined with `pub`/`mut`,
// and is reached as `Type.member` or by its bare name inside the declaring type. Static state is
// shared by every instance and lives as long as the generated Java class.
type Visitors {

    static mut count := 0
    static label := "visits"
    static pub labelPlus := label .. "!"  // an initializer may read an earlier static field
    static pub kind := "counter"
    static pub mut limit := 2
    static pub doubledLimit := twice(limit)  // and may call a static function

    pub func Visitors() { }

    pub func visit() -> Integer {
        count = count + 1
        return count
    }

    static func twice(value: Integer) -> Integer {
        return value + value
    }

    static pub func reset() {
        count = 0
    }

    static pub func report() -> String {
        return label .. "=" .. count
    }

    static pub func make() -> Visitors {
        return Visitors()  // a static function may construct its own type
    }
}

firstVisitor := Visitors()
secondVisitor := Visitors()
firstVisit := firstVisitor.visit()
secondVisit := secondVisitor.visit()
out.writeLine("static: " .. firstVisit .. " " .. secondVisit .. " " .. Visitors.report())
out.writeLine("static init: " .. Visitors.labelPlus .. " " .. Visitors.doubledLimit)
out.writeLine("static factory: " .. Visitors.make().type())
Visitors.reset()
Visitors.limit = 5
out.writeLine("static reset: " .. Visitors.report() .. " " .. Visitors.kind .. Visitors.limit)

// A static field is immutable unless `mut`; `Visitors.kind = "other"` is SOL-T010. A static field
// needs an initializer, and static members of a generic type may not use its type parameters.
// `static` is never a type or module modifier, and it is reached through the type:
//   visitor.count            -> instance access to a static member
//   Visitors.visit()         -> type-name access to an instance member
//   static type X { }        -> not a declaration form
//   static func f<T>(...)    -> generic static functions are not part of the language

// Static functions whose source names are Object contract methods are still static members; the
// generated Java name gets a '$function' suffix, while Solvik keeps the original name.
type StaticNames {

    pub func StaticNames() { }

    static pub func toString() -> String { return "static-toString" }
    static pub func hashCode() -> Integer { return 7 }
    static pub func equals(value: Any) -> Boolean { return value == 1 }
}
out.writeLine("static contract names: " .. StaticNames.toString() .. " "
    .. StaticNames.hashCode() .. " " .. StaticNames.equals(1) .. " " .. StaticNames.equals(2))

/*
 * Includes, aliases, and modules (§1, §3, §10)
 */

// `include { ... }` names files, relative to this file, and contributes declarations only: their
// modules merge by name, and only this file's statements run. `include.sol` declares
// `module showcase`. A file path decides nothing about the namespaces inside it - the file's own
// `module` blocks do, which is why `showcase::issue` is written the same way however the file is
// named or placed. Reopening a module never discovers files: every file is reached by an explicit
// include. Entries are separated by a newline or `;`, a lone `;` is layout, and the block is
// required even for one path.

include {
    "include.sol"
    "./include.sol"  // the same physical file again: loaded once
    ;
    "include-extra.sol"  // includes include.sol itself: this is a diamond, not a duplicate
}
include { "include-extra.sol" }  // a one-entry block on one line: still loaded once

// `alias { ... }` binds a spelling that is private to this file. Each entry writes the existing
// canonical symbol first and the local name second. An alias adds no nominal identity: `Ticket`
// and `showcase::Ticket` are one type, and the generated Java names the canonical one. A module
// alias stands at the head of a qualified path, a type alias works wherever its type is written,
// and a trait alias works in `implements` and in the qualified default form. Blocks are file-wide
// and order independent, and an alias never crosses a file boundary.

alias {
    showcase::Ticket Ticket; showcase::Math ShowMath
    showcase::Echo EchoContract
    showcase desk
}

ticket := Ticket(7)  // the type alias constructs `showcase::Ticket`
board := desk::Board("sprint")  // the module alias reaches a nested module type
explicitTicket: Ticket = Ticket(8)
out.writeLine("pub field read: " .. ticket.id)
out.writeLine("alias: " .. ticket .. " " .. desk::label(ticket) .. " " .. board.first())
out.writeLine("alias: " .. desk::nextAfter(41) .. " " .. ticket.type() .. " " .. board.type())
out.writeLine("alias diamond: " .. desk::extraTag())
// A native type is an ordinary type to an alias: static members are reached through the alias.
out.writeLine("alias static: " .. ShowMath.abs(-3) .. " " .. ShowMath.max(2, 5))
// An alias block is order independent: `Late` is declared in the block below this use.
early := Late("early")
alias { showcase::Board Late }
late := Late("later")
out.writeLine("alias order: " .. early.name .. " " .. late.name)
// Aliases declared inside an included file stay there; this file reaches the same declarations
// through its own aliases and through canonical paths.
out.writeLine("alias in include: " .. desk::bump(Ticket(3)).id .. " "
    .. showcase::EchoBox().echo("boxed"))

// The standard library needs no include: every `.sol` file the compiler packages under
// src/main/resources is part of every program, loaded before the program's own files.
// Map.sol, Stack.sol, and List.sol declare `solvik::collection` over java.util.HashMap,
// java.util.Stack, and java.util.ArrayList; Iterator.sol, Set.sol, and Collection.sol declare the
// iteration protocol and the two views a map publishes (`keys()` and `values()`), over
// java.util.Iterator, java.util.Set, and java.util.Collection; UUID.sol declares the Solvik-authored
// `solvik::util::UUID` below. There are no `java::collections` or HashMap aliases, and the
// names stay qualified: `Map` alone is never in scope.

// This file reopens `module showcase` too. The private `Counter` declared in include.sol is
// visible here because a declaration is private to its module, not to its file; the module-level
// `label` overloads merge with the included `label(ticket: Ticket)` across contributors.
module showcase {

    pub func counter() -> String {
        counter := Counter()
        return "counter=" .. counter.bump()
    }

    pub func label(value: Integer) -> String { return "int:" .. value }
    pub func label(value: String) -> String { return "string:" .. value }

    func prefix() -> String { return "module-private" }

    pub func hidden() -> String { return prefix() }
}
out.writeLine("module merge: " .. showcase::counter() .. " " .. showcase::label(1) .. " "
    .. showcase::label("x") .. " " .. showcase::hidden())

// A trait declared in an included file still supports the qualified default form. The explicit
// call skips virtual dispatch and invokes that trait's default body.
type Echoer implements EchoContract {
    pub func Echoer() { }
    pub func echo(value: String) -> String {
        return "override:" .. EchoContract.echo(self, value)
    }
}
out.writeLine("qualified trait default: " .. Echoer().echo("x") .. " " .. Echoer().echo("y"))
// Trait.default(self, ...) is valid only inside an instance function of a concrete type that
// directly implements the trait. `self` is the first argument and is not a declared parameter, and
// the selected trait overload must have a body (SOL-T001).

/*
 * Standard-library collections (§11)
 */

// No include: these types are packaged with the compiler. Solvik module `solvik::collection`
// emits Java package `solvik.collection`; the Java target class is named in each binding, and
// the resource path decides nothing about the namespace the file declares.

// 1. Map (java.util.HashMap) over Any: a construction names its type arguments, because a
// binding's type is known where it is declared (§8.2).
mapRaw := solvik::collection::Map<Any, Any>()
mapRaw.put("alpha", 1)
mapRaw.put("beta", 2)
out.writeLine("map: " .. mapRaw.size() .. " " .. mapRaw.get("alpha") .. " "
    .. mapRaw.containsKey("beta") .. " " .. mapRaw.containsValue(9))
out.writeLine("map remove: " .. mapRaw.removeValue("beta") .. " " .. mapRaw.size())
out.writeLine("map text: " .. mapRaw.toString().size() > 0)
mapRaw.clear()
out.writeLine("map clear: " .. mapRaw.size() .. " " .. mapRaw.isEmpty())

// 2. Map with generics: a typed key and value, checked by the compiler. `get` returns `V?`,
// because java.util.HashMap.get can return null (§11).
mapTyped := solvik::collection::Map<String, Integer>()
mapTyped.put("ann", 10)
mapTyped.put("bob", 20)
annScore := mapTyped.get("ann")  // Integer?
if annScore == Null {
    out.writeLine("map typed: absent")
} else {
    out.writeLine("map typed: " .. annScore + mapTyped.size() .. " " .. mapTyped.size() .. " "
        .. mapTyped.isEmpty())
}

// 3. Stack over Any: peek() and pop() return `T?` because a Java Stack may be empty.
stackRaw := solvik::collection::Stack<Any>()
stackRaw.push("first")
stackRaw.push("second")
out.writeLine("stack: " .. stackRaw.size() .. " " .. stackRaw.peek() .. " "
    .. stackRaw.search("first"))
out.writeLine("stack text: " .. stackRaw)
out.writeLine("stack pop: " .. stackRaw.pop() .. " " .. stackRaw.size() .. " " .. stackRaw.empty())

// 4. Stack with generics.
stackTyped := solvik::collection::Stack<Integer>()
stackTyped.push(1)
stackTyped.push(2)
stackTyped.push(3)
firstPop := stackTyped.pop()
secondPop := stackTyped.pop()
thirdPop := stackTyped.pop()
if firstPop != Null && secondPop != Null && thirdPop != Null {
    out.writeLine("stack typed: " .. firstPop + secondPop + thirdPop)
}

// 5. ArrayList through the `List` binding, over Any.
listRaw := solvik::collection::List<Any>()
listRaw.add("alpha")
listRaw.add("beta")
listRaw.add("gamma")
out.writeLine("list: " .. listRaw.size() .. " " .. listRaw.get(0) .. " "
    .. listRaw.indexOf("gamma"))
out.writeLine("list text: " .. listRaw)
listRaw.set(1, "BETA")
out.writeLine("list: " .. listRaw.get(1) .. " " .. listRaw.contains("beta") .. " "
    .. listRaw.contains("BETA"))
out.writeLine("list remove: " .. listRaw.remove(0) .. " " .. listRaw.size() .. " "
    .. listRaw.removeValue("BETA"))
listRaw.clear()
out.writeLine("list clear: " .. listRaw.size() .. " " .. listRaw.isEmpty() .. " " .. listRaw)

// 6. ArrayList with generics: `List<T>` is a java.util.ArrayList<T>, and get() returns T?.
listTyped := solvik::collection::List<Integer>()
listTyped.add(10)
listTyped.add(20)
listTyped.add(30)
firstItem := listTyped.get(0)
secondItem := listTyped.get(1)
thirdItem := listTyped.get(2)
if firstItem != Null && secondItem != Null && thirdItem != Null {
    out.writeLine("list sum: " .. firstItem + secondItem + thirdItem .. " " .. listTyped.size())
}

// 7. `for ... in` over a collection: a type participates by declaring
// `pub func iterator() -> solvik::collection::Iterator<X>`, and the loop name is bound to `X`
// (never `X?`). `List`, `Stack`, and a map's key and value views declare it; `Map` itself does not,
// because a map entry has no Solvik type.
iterationList := solvik::collection::List<Integer>()
iterationList.add(4)
iterationList.add(5)
iterationList.add(6)
mut listTotal := 0
for value in iterationList {
    listTotal = listTotal + value
}

// The same protocol over a Stack reads the stack's own order, bottom to top.
iterationStack := solvik::collection::Stack<String>()
iterationStack.push("a")
iterationStack.push("b")
mut stackText := ""
for word in iterationStack {
    stackText = stackText .. word
}

// A nullable element type stays nullable, so the body narrows each element before using it.
nullableItems := solvik::collection::List<String?>()
nullableItems.add("ada")
nullableItems.add(Null)
mut present := 0
for name in nullableItems {
    if name != Null {
        present = present + 1
    }
}
out.writeLine("iteration: " .. listTotal .. " " .. stackText .. " " .. present)

// The protocol is ordinary library surface: a user type participates by declaring `iterator()`
// itself, and the loop reads whatever that member returns.
type Countdown {
    pub values := solvik::collection::List<Integer>()

    pub func Countdown(seed: Integer) {
        values.add(seed)
        values.add(seed - 1)
    }

    pub func iterator() -> solvik::collection::Iterator<Integer> {
        return self.values.iterator()
    }
}
ticker := Countdown(3)
mut tickerTotal := 0
for value in ticker {
    tickerTotal = tickerTotal + value
}
out.writeLine("protocol: " .. tickerTotal)

// A map is enumerated through its two views: `keys()` is a `solvik::collection::Set<K>` and
// `values()` a `solvik::collection::Collection<V>`, and both publish the same protocol, so the loop
// names are `K` and `V`. They are the JDK's live views, not copies, and a java.util.HashMap has no
// defined order, so the corpus prints order-free totals. Entry-wise iteration is still rejected:
// `for entry in scores` needs a map-entry type the language does not have.
iterationScores := solvik::collection::Map<String, Integer>()
iterationScores.put("north", 3)
iterationScores.put("south", 4)
mut keyLetters := 0
for region in iterationScores.keys() {
    keyLetters = keyLetters + region.size()
}
mut scoreTotal := 0
for amount in iterationScores.values() {
    scoreTotal = scoreTotal + amount
}
out.writeLine("map iteration: " .. iterationScores.keys().size() .. " "
    .. iterationScores.values().size() .. " " .. iterationScores.keys().contains("north") .. " "
    .. iterationScores.values().contains(4) .. " " .. iterationScores.keys().isEmpty() .. " "
    .. iterationScores.values().isEmpty() .. " " .. keyLetters + scoreTotal)

/*
 * solvik::util::UUID (value type in Solvik)
 */

// The packaged solvik/util/UUID.sol declares `solvik::util::UUID`, a value type written in
// Solvik and used with no include: a version 7 identifier
// (RFC 9562) is a 48-bit millisecond timestamp plus 74 random bits, rendered as canonical
// lowercase text. `now()` and `parse()` are the only ways to make one - the constructor is
// private, so a value is always well formed. Its JDK helpers (Hex, Clock, Random) are
// module-private and never appear in a program.

uuid := solvik::util::UUID.now()
out.writeLine("uuid: " .. uuid .. " stamp: " .. uuid.timestamp() .. " type: " .. uuid.type())
uuidText := uuid.toString()
out.writeLine("uuid shape: " .. uuidText.size() .. " " .. uuidText.substring(14, 15) .. " "
    .. "89ab".indexOf(uuidText.substring(19, 20)) >= 0)

// Equality is by value: parse() rebuilds an equal value, while `==` stays reference identity.
// parse() returns `UUID?`, because it reports rejection by returning Null.
uuidCopy := solvik::util::UUID.parse(uuidText)
if uuidCopy != Null {
    out.writeLine("uuid equal: " .. uuidCopy.equals(uuid) .. " "
        .. uuidCopy.hashCode() == uuid.hashCode() .. " " .. uuidCopy == uuid .. " "
        .. uuid.timestamp() == uuidCopy.timestamp())
}

// compareTo() orders by timestamp, then by the random fields, so ids sort by creation time.
// Fixed text keeps this section's output stable.
earlyId := solvik::util::UUID.parse("0190f2a4-7b3c-7a1d-8e2f-3c4d5e6f7a8b")
laterId := solvik::util::UUID.parse("0190f2a4-7b3d-7a1d-8e2f-3c4d5e6f7a8b")
if earlyId != Null && laterId != Null {
    out.writeLine("uuid order: " .. earlyId.compareTo(laterId) < 0 .. " "
        .. laterId.compareTo(earlyId) > 0 .. " " .. earlyId.compareTo(earlyId) == 0)
    out.writeLine("uuid stamps: " .. earlyId.timestamp() .. " "
        .. laterId.timestamp() - earlyId.timestamp())
}

// parse() accepts either case and rejects anything that is not canonical version 7 text.
out.writeLine("uuid parse: "
    .. solvik::util::UUID.parse("0190F2A4-7B3C-7A1D-8E2F-3C4D5E6F7A8B") .. " "
    .. solvik::util::UUID.parse("0190f2a4-7b3c-4a1d-8e2f-3c4d5e6f7a8b") == Null .. " "
    .. solvik::util::UUID.parse("junk") == Null)

// Value equality and hashing reach Java's HashMap through the generated overrides, so a UUID
// works as a key and a reparsed value finds the same entry.
uuidOwners := solvik::collection::Map<solvik::util::UUID, String>()
uuidOwners.put(uuid, "ann")
reparsed := solvik::util::UUID.parse(uuid.toString())
if reparsed != Null {
    out.writeLine("uuid key: " .. uuidOwners.get(reparsed) .. " " .. uuidOwners.size())
}

/*
 * Type bodies resolve whole-program names
 */

// A type body resolves against every declaration, so a `type` may hold a packaged collection, take
// one as a parameter, and construct a module type or a type declared later in this file. Nothing
// here depends on where the declarations sit.
type Basket {
    pub items := solvik::collection::List<Integer>()
    pub origin := shapes::Point(2, 3)
    pub seal := Seal("root")

    pub func Basket() { }

    pub func absorb(source: solvik::collection::List<Integer>) -> Integer {
        self.items.add(source.size())
        return self.items.size() + source.size()
    }
}

// `Basket` above constructs this type in a field initializer, though it is declared below.
type Seal {
    pub label := ""

    pub func Seal(label: String) {
        self.label = label
    }
}

basket := Basket()
basketSource := solvik::collection::List<Integer>()
basketSource.add(7)
basketSource.add(8)
out.writeLine("type body: " .. basket.absorb(basketSource) .. " " .. basket.origin.x .. " "
    .. basket.seal.label)

/*
 * Modules
 */

// A module groups type and function names, so this `Point` may coexist with the global one
// above. `::` qualifies a module-qualified name; inside the module the short name works.
// Module blocks merge across included files; declarations are module-private unless `pub`.

module shapes {

    pub type Point {

        pub mut x := 0
        pub mut y := 0

        pub func Point(x: Integer, y: Integer) {
            self.x = x
            self.y = y
        }

        pub func shifted(dx: Integer) -> Point {
            return Point(x + dx, y)  // the module's Point, taken from scope
        }

        pub func toString() -> String {
            return "shape(" .. x .. ", " .. y .. ")"
        }
    }

    pub func area(width: Integer, height: Integer) -> Integer {
        return width * height
    }

    pub func scale(value: Integer, side: Integer) -> Integer {
        return value * side  // a module function, called plainly inside it
    }

    pub func square(side: Integer) -> Integer {
        return scale(side, side)
    }

    type Ledger {  // private to module shapes
        pub mut total := 0

        pub func Ledger(total: Integer) {
            self.total = total
        }

        pub func doubled() -> Integer {
            return total * 2
        }
    }

    pub func audit(start: Integer) -> String {
        ledger := Ledger(start)  // a pub function may use a private type inside
        return ledger.total .. "->" .. ledger.doubled()
    }
}

module shapes {

    // A second block of the same module merges with the first, so this may name Point above.
    pub func describe(point: Point) -> String {
        return point .. " area=" .. square(point.x)
    }

    pub type Report {  // a pub type, with a private type in its body

        static pub category := "audit"
        pub mut note := "report"

        pub func Report() {
            note = "report"
        }

        static pub func kind() -> String {
            return "report-kind"
        }

        pub func summary(total: Integer) -> String {
            ledger := Ledger(total)  // the private type again, still inside this module
            return note .. ":" .. ledger.doubled()
        }
    }
}

// Modules nest to any depth: `shapes::solids::volume` is Java `shapes.solids.Solvik$Functions`.
// The same child name under different parents is a different namespace. Duplicate types,
// functions, or modules and cross-kind collisions are SOL-T001, not partial declarations.
module shapes {
    module solids {
        pub func volume(edge: Integer) -> Integer {
            return edge * edge * edge
        }

        pub type Cube {
            pub mut edge := 0

            pub func Cube(edge: Integer) {
                self.edge = edge
            }

            pub func volume() -> Integer {
                return edge * edge * edge
            }
        }
    }
}

// The same nested module path may be reopened in another block, merging its declarations.
module shapes {
    module solids {
        pub func surface(edge: Integer) -> Integer {
            return 6 * edge * edge
        }
    }
}

// A child name is qualified by its parent: `widgets::solids` and `shapes::solids` are different
// namespaces even though both children are called `solids`.
module widgets {
    module solids {
        pub func kind() -> String { return "widget-solid" }
    }
}

// An empty module block is legal and contributes nothing.
module empty { }

// A user-authored root module named `java` still maps to Java package `solvik.java`, because Java
// reserves `java.*` for the JDK. The source name stays `java`.
module java {
    module sample {
        pub func tag() -> String { return "java-root" }
        pub type Widget {
            pub name := "widget"
            pub func Widget() { }
            pub func toString() -> String { return "java:" .. name }
        }
    }
}

shapeCorner := shapes::Point(2, 3)
out.writeLine("modules: " .. shapeCorner .. " " .. shapeCorner.type())
out.writeLine("modules: " .. shapes::area(2, 3) .. " " .. shapes::square(4))
out.writeLine("modules: " .. shapes::describe(shapeCorner.shifted(1)))
out.writeLine("modules: " .. Point(9, 9) .. " and " .. shapes::Point(9, 9))
out.writeLine("distinct types: " .. origin === shapeCorner .. " " .. origin == shapeCorner)
out.writeLine("modules: " .. shapes::audit(21) .. " " .. shapes::Report().summary(4))
out.writeLine("module static: " .. shapes::Report.kind() .. " " .. shapes::Report.category)
out.writeLine("nested modules: " .. shapes::solids::volume(3) .. " "
    .. shapes::solids::Cube(4).volume() .. " " .. shapes::solids::Cube(2).type())
out.writeLine("nested merge: " .. shapes::solids::surface(2) .. " " .. widgets::solids::kind())
out.writeLine("java root module: " .. java::sample::tag() .. " " .. java::sample::Widget() .. " "
    .. java::sample::Widget().type())
out.writeLine("static native: " .. showcase::Math.abs(-7) .. " " .. showcase::Math.max(3, 9) .. " "
    .. showcase::Math.sqrt(81.0) .. " " .. showcase::Math.floor(2.9))

// An instance binding wraps one Java object, and `native "name"` picks the Java member each
// Solvik declaration selects; a declaration with no return type would discard the Java result.
die := showcase::Die()
out.writeLine("instance native: " .. die.roll(6) >= 0 .. " " .. die.roll(6) < 6 .. " "
    .. die.type())
out.writeLine("renamed native: " .. showcase::NumberText.text(42) .. " "
    .. showcase::NumberText.parse("7") .. " "
    .. showcase::NumberText.parse(showcase::NumberText.text(-3)))

// `hashCode` is routed to the Java list's `size()` by the declaration; `toString` is not declared,
// so it renders the delegate, and `type()` always reports the Solvik name.
bag := showcase::Bag()
bag.put("one")
bag.put("two")
out.writeLine("binding contract: " .. bag.hashCode() .. " " .. bag.count() .. " " .. bag .. " "
    .. bag.type())

// A native type is a reference type too, so it can hold Null; the type stays the binding type.
func missingBag(value: showcase::Bag?) -> Boolean { return value == Null }
out.writeLine("native null: " .. missingBag(Null) .. " " .. missingBag(bag))

// A declared `toString()` routes the render contract to the Java method and puts the override on
// the adapter. `append` has no return type, so its Java result is discarded as a statement.
builder := showcase::Text()
builder.append("hello")
builder.append(" world")
out.writeLine("declared toString: " .. builder .. " " .. builder.toString() .. " "
    .. builder.type())

// A checked Java exception from a native call surfaces as SOL-R011; unchecked ones propagate.
// Invalid classes, unsupported shapes, and ambiguous overloads are SOL-T013 at the declaration.
// `type()` is final on every value, including a binding, so a binding cannot declare it. A native
// type target must be a public concrete class; a native trait target must be a public interface.
// Type-parameter counts must match where supported, and each declaration selects exactly one public
// Java overload by exact Solvik types (`Object` parameters are written `Any`).

// A binding that declares equals and hashCode compares the Java objects it wraps, so an equal value
// finds its entry even though the two values are distinct objects. `==` stays reference identity.
owed := showcase::Amount("1000")
paid := showcase::Amount("1000")
ledger := solvik::collection::Map<showcase::Amount, Integer>()
ledger.put(owed, 7)
out.writeLine("binding equality: " .. owed.equals(paid) .. " " .. owed == paid .. " "
    .. owed === paid .. " " .. owed.text() .. " " .. ledger.get(paid) .. " "
    .. ledger.get(showcase::Amount("2000")))

/*
 * Generics
 */

// A generic field has no universal non-null initial value, so it is declared `T?` and initialized
// to Null (§3.3). The constructor stores the value, and a method that returns `T` would have to
// prove the field present first, so the API returns `T?`. A type has no implicit constructor, so
// every instance is built by a declared one.
type Box<T> {

    mut value: T? = Null

    pub func Box(value: T) {
        self.value = value  // `self.` because the parameter is also named value
    }

    pub func get() -> T? {
        return value  // a plain member name
    }

    pub func sameAs(other: T) -> Boolean {
        return self.value == other  // equality on a generic parameter is boxed equality
    }
}

type Pair<A, B> {

    first: A? = Null
    second: B? = Null

    pub func Pair(first: A, second: B) {
        self.first = first  // `self.` because the parameters share these names
        self.second = second
    }

    pub func describe() -> String {
        return "(" .. first .. ", " .. second .. ")"
    }
}
// A generic type may hold arrays of its own type parameter. The element type is `T?`, so the
// literal may start empty and the omitted elements are Null.
type Bucket<T> {

    pub mut items: T?[3] = T?[3] { }

    mut count := 0

    pub func Bucket(seed: T) {
        items[0] = seed
        count = 1
    }

    pub func put(value: T) {
        items[count] = value
        count = count + 1
    }

    pub func get(index: Integer) -> T? {
        return items[index]
    }

    pub func count() -> Integer {
        return count  // the field: a plain name means the member (§3)
    }
}
letterBucket := Bucket("a")
letterBucket.put("b")
letterBucket.put("c")
numberBucket := Bucket(7)
numberBucket.put(8)
out.writeLine("generic array: " .. letterBucket.count() .. " " .. letterBucket.items)
out.writeLine("generic array numbers: " .. numberBucket.get(1) .. " " .. numberBucket.count())

numberBox := Box(41)  // Box<Integer>, inferred
textBox := Box("hello")  // Box<String>, inferred
pair := Pair(1, "one")  // Pair<Integer, String>
nested := Box(Box(1))  // Box<Box<Integer>>
boxedValue := numberBox.get()  // Integer?
if boxedValue != Null {
    out.writeLine("generics: " .. boxedValue + 1 .. " " .. numberBox.type())
}
out.writeLine("generic equality: " .. numberBox.sameAs(41) .. " " .. textBox.sameAs("hello") .. " "
    .. textBox.sameAs("other"))
out.writeLine("generics: " .. textBox.get() .. " " .. textBox.type())
out.writeLine("generics: " .. pair.describe() .. " " .. pair.type())
innerBox := nested.get()  // Box<Integer>?
if innerBox != Null {
    out.writeLine("generics: " .. innerBox.get() .. " " .. nested.type())
}
mut nullableBox? := Box(1)  // inferred nullable: Box<Integer>?
nullableBox = Null
out.writeLine("generic null: " .. nullableBox .. " " .. nullableBox == Null)
nullableBox = Box(2)
out.writeLine("generic restored: " .. nullableBox.get() .. " " .. nullableBox.type())

// A static member of a generic type may not use the type's parameters, because Java's static
// context has no instance type arguments: `static func make(value: T)` is SOL-T001.

/*
 * Traits, overloads, and virtual threads
 */

func overloadLabel(value: Integer) -> String { return "integer:" .. value }
func overloadLabel(value: String) -> String { return "string:" .. value }
// Return type, parameter names, visibility, and `static` do not distinguish a signature; only the
// name plus ordered parameter types do.
// Calls select the unique most-specific applicable overload: Integer beats Number beats Any.
func rank(value: Any) -> String { return "any" }
func rank(value: Number) -> String { return "number" }
func rank(value: Integer) -> String { return "integer" }
out.writeLine("overloads: " .. overloadLabel(7) .. " " .. overloadLabel("seven"))
out.writeLine("specific overloads: " .. rank(1) .. " " .. rank(1.5) .. " " .. rank("text"))
// Exact duplicate signatures and signatures with the same Java erasure are SOL-T001; an
// inapplicable call is SOL-T001, and equally good candidates are SOL-T014.

trait NamedTask {
    pub func taskName() -> String

    pub func summary() -> String {
        return "task=" .. taskName()
    }

    pub func format(value: Integer) -> String { return "integer=" .. value }
    pub func format(value: String) -> String { return "string=" .. value }
    pub func ready() { }  // an empty block is still a default, not a requirement
}

trait RequiresSummary {
    pub func summary() -> String
}

trait LabeledCloseable native "java.lang.AutoCloseable" {
    pub func close()
    pub func closeLabel() -> String { return "closed by Solvik default" }
}

trait Retry {
    pub func attempts() -> Integer
    pub func attemptOnce() -> Boolean
    pub func delayMs(attempt: Integer) -> Long { return 100L * Long(attempt) }
}

// Two implemented traits may contribute the same default signature. That is ambiguous until the
// concrete type declares an override; the override can compose both defaults explicitly.
trait First {
    pub func name() -> String { return "first" }
}

trait Second {
    pub func name() -> String { return "second" }
}

type Combined implements First, Second {
    pub func Combined() { }
    pub func name() -> String {
        return First.name(self) .. "+" .. Second.name(self)
    }
}
// A trait has no fields, constructors, static functions, generics, or inheritance, and cannot be
// constructed. A default body sees `self` as the trait, so reading an implementing type's fields
// or private behavior is SOL-T001.

type SumTask implements solvik::concurrency::Task, NamedTask, RequiresSummary {

    left := 0
    right := 0
    mut answer := 0

    pub func SumTask(left: Integer, right: Integer) {
        self.left = left
        self.right = right
    }

    pub func run() {
        answer = left + right
    }

    pub func taskName() -> String { return "sum" }

    pub func result() -> Integer {
        return answer
    }
}

type LoudTask implements NamedTask {
    pub func LoudTask() { }
    pub func taskName() -> String { return "loud" }
    pub func summary() -> String { return "OVERRIDE:" .. NamedTask.summary(self) }
}

type ExampleCloseable implements LabeledCloseable {
    pub func ExampleCloseable() { }
    pub func close() { }
    pub func closeLabel() -> String {
        return "wrapped " .. LabeledCloseable.closeLabel(self)
    }
}

type Backoff implements Retry {
    pub func Backoff() { }
    pub func attempts() -> Integer { return 3 }
    pub func attemptOnce() -> Boolean { return true }
    pub func delayMs(attempt: Integer) -> Long {
        return 2L * Retry.delayMs(self, attempt)
    }
}

func describeTask(task: NamedTask) -> String { return task.summary() }
func missingTask(task: NamedTask?) -> Boolean { return task == Null }

sumTask := SumTask(20, 22)
sumTask.ready()
out.writeLine("trait defaults: " .. sumTask.summary() .. " " .. describeTask(sumTask) .. " "
    .. sumTask.format(7) .. " " .. sumTask.format("seven") .. " " .. describeTask(LoudTask()))
out.writeLine("trait null: " .. missingTask(Null) .. " " .. missingTask(sumTask))
out.writeLine("composed defaults: " .. Combined().name())
exampleCloseable := ExampleCloseable()
exampleCloseable.close()
out.writeLine("native trait default: " .. exampleCloseable.closeLabel())
out.writeLine("reused trait default: " .. Backoff().delayMs(2))
sumThread := solvik::concurrency::Thread.start(sumTask)
sumThread.await()
sumThread.await(1L)  // the completed-thread timed form is deterministic
sumThread.await(0L)  // zero waits indefinitely, like the bare form; the thread is already done
out.writeLine("virtual thread result: " .. sumTask.result() .. " alive=" .. sumThread.isAlive())
// A completed thread can still take an interrupt request; its interrupt status is observable while
// it stays terminated. This exercises both remaining Thread methods deterministically.
sumThread.interrupt()
out.writeLine("thread interrupt: " .. sumThread.isInterrupted() .. " alive=" .. sumThread.isAlive())
// Thread.start always uses Java 25 virtual threads; there is no platform-thread constructor and no
// isVirtual() query. A positive await may time out, so isAlive() is checked before relying on
// state.
// `0L` waits indefinitely and a negative duration follows Java's IllegalArgumentException.
// An uncaught task exception terminates its thread but is not rethrown by await; interruption of
// the awaiting thread follows the existing SOL-R011 native-call policy.

/*
 * Synchronization (§7.5)
 */

// A `sync` block holds its monitor's Java monitor for the whole block. The release is Java's own
// behavior, so a return, a break, a continue, a raise, an abort, or exit() cannot skip it and no
// `try`/`finally` is written for it. The monitor is a user value or an array, never a boxed scalar.

type GuardedCounter {
    pub mut total := 0
    pub func GuardedCounter() { }
    pub func bump() { total = total + 1 }
}

type GuardedBumper implements solvik::concurrency::Task {
    counter := GuardedCounter()
    rounds := 0
    pub func GuardedBumper(counter: GuardedCounter, rounds: Integer) {
        self.counter = counter
        self.rounds = rounds
    }
    pub func run() {
        mut done := 0
        while done < rounds {
            sync counter {
                counter.bump()
            }
            done = done + 1
        }
    }
}

guarded := GuardedCounter()
guardedFirst := solvik::concurrency::Thread.start(GuardedBumper(guarded, 500))
guardedSecond := solvik::concurrency::Thread.start(GuardedBumper(guarded, 500))
guardedFirst.await()
guardedSecond.await()
// Two tasks, 500 guarded increments each: the exact total is the same on every run.
out.writeLine("sync guarded: " .. guarded.total)
// Blocks on one monitor are reentrant, and a return or a raise inside the block still releases it.
sync guarded {
    sync guarded {
        guarded.bump()
    }
}
out.writeLine("sync nested: " .. guarded.total)
func bumpInsideSync(counter: GuardedCounter) -> Integer {
    sync counter {
        counter.bump()
        return counter.total
    }
}
out.writeLine("sync returned: " .. bumpInsideSync(guarded))
try {
    sync guarded {
        raise Exception(4009, "raised inside sync")
    }
} catch (problem: Exception) {
    out.writeLine("sync raise: " .. problem)
}
sync guarded {
    guarded.bump()
}
out.writeLine("sync after raise: " .. guarded.total)

/*
 * Exception handling (§7.4)
 */

type FailureCodes {
    static pub Denied := 4001
    static pub Missing := 4002
    static pub Wrapped := 4003
}

func requirePositive(value: Integer) {
    if value <= 0 {
        raise Exception(FailureCodes.Denied, "value must be positive, found " .. value)
    }
    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine("accepted " .. value)
}

// One exception type carries an Integer code, so the code is what a handler dispatches on.
try {
    requirePositive(0)
} catch (problem: Exception) {
    out.writeLine("caught: " .. problem)
    if problem.getCode() == FailureCodes.Denied {
        out.writeLine("dispatch: denied " .. problem.getMessage())
    }
    switch problem.getCode() {
        case 4001 { out.writeLine("switch: denied") }
        case 4002 { out.writeLine("switch: missing") }
        default { out.writeLine("switch: other") }
    }
}
requirePositive(5)

// finally runs on the normal path, after a return, and around loop control.
func cleanupReturn(flag: Boolean) -> Integer {
    try {
        if flag {
            return 1
        }
        return 2
    } finally {
        solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine("cleanup after return")
    }
}
out.writeLine("returned " .. cleanupReturn(true) .. " " .. cleanupReturn(false))

mut trace := ""
for step in Integer[3] {1, 2, 3} {
    try {
        if step == 2 {
            continue
        }
        if step == 3 {
            break
        }
        trace ..= String(step)
    } finally {
        trace ..= "!"
    }
}
out.writeLine("finally trace: " .. trace)

// try/finally without a catch: the cleanup runs and the raise keeps traveling outwards.
try {
    try {
        raise Exception(FailureCodes.Missing, "inner failure")
    } finally {
        out.writeLine("inner cleanup")
    }
} catch (outermost: Exception) {
    out.writeLine("outer caught: " .. outermost.getCode() .. " " .. outermost.getMessage())
}

// A handler may raise again, and a nullable Exception narrows like any other nullable value.
mut heldOver: Exception? = Null
try {
    try {
        raise Exception(FailureCodes.Wrapped, "wrapped")
    } catch (inner: Exception) {
        wrapped := Exception(inner.getCode() + 1, inner.getMessage() .. " again")
        heldOver = wrapped
        raise wrapped
    }
} catch (recaught: Exception) {
    out.writeLine("reraised: " .. recaught)
}
if heldOver != Null {
    out.writeLine("heldOver: " .. heldOver.type() .. " " .. heldOver.getCode() .. " "
        .. String(heldOver))
}

// An exception is an ordinary value: it can be built, held, and raised where it is needed.
planned := Exception(FailureCodes.Denied, "planned failure")
try {
    raise planned
} catch (held: Exception) {
    out.writeLine("planned: " .. held.getCode() .. " " .. held.getMessage())
}

/*
 * Exit status
 */

boxedCount := numberBox.get()
if boxedCount != Null && boxedCount > 100 {
    out.writeLine("unexpected")
    exit(1)
}
out.writeLine("final status code: 0")
exit()  // bare exit(); `exit(1)` above is the code form

/*
 * What this file cannot show
 */

// This corpus demonstrates valid surface. The cases below cannot appear in a program that runs to
// completion: they are compile-time rejections unless marked as runtime aborts.
//
// Lexical (SOL-L001..SOL-L007):
//   @                   unexpected character (L001)
//   "unterminated       unterminated string (L002)
//   "\q"                unknown escape (L003)
//   r#"unterminated     unterminated raw string (L004)
//   /* open             unterminated block comment (L005)
//   \ at the start of a file (L006): a continuation must follow a newline
//   1l, 1X              invalid or lowercase numeric suffix (L007)
//
// Syntax (SOL-P001..SOL-P008):
//   func f( { }         expected a parameter name (P002)
//   an unexpected token (P001)
//   1 + 2               only an assignment or call may stand as a statement (P003)
//   static func helper() at top level, or `static type X` (P004): static is a type-member modifier
//   var x := 1 (P006) and null (P006): both spellings were removed by the declaration revision
//   x = 1 as a declaration (P007), Integer[2] = x (P007), or Integer?? (P008)
//
// Types, names, visibility, static members, generics, and overloads (SOL-T001..SOL-T017):
//   a function or method whose name matches a type name (T001)
//   duplicate declarations, redeclaring a name in one scope, or `outer outer x` (T001)
//   `if 1` where Boolean is required (T001)
//   a contract override that is not public or exact (T001)
//   an unsupported construct (T002), a type name that does not start uppercase (T003), or
//   unreachable code after a return (T004)
//   `return` outside a function (T005), an invalid `case regex` pattern (T006), or
//   `break`/`continue` outside a loop (T007)
//   a literal operation that must fail: 1 / 0, 1 % 0, Byte(128), or text.split("") (T008)
//   written type arguments, such as Box<Integer>, which are inferred at construction (T009)
//   a write to an immutable field, such as `ticket.id = 1` (T010), or to an array's member,
//     such as `values.size = 2`; an array's call form `values.size()` is not a function (T001)
//   shapes::Ledger(1) or shapes::scale(1, 2) from outside their module (T011)
//   a `pub` signature that names a private type (T012): a pub body may use it, a signature may not
//   an exit code literal outside 0..255 (T001)
//   an invalid, unsupported, or ambiguous native binding (T013)
//   `choose(Null)` where two unrelated nullable overloads are equally good (T014)
//   `for entry in map` over a `Map` (T001): a map entry has no Solvik type, so a map is enumerated
//     through its `keys()` and `values()` views (§7, §11.3)
//   overloads that differ only by return type, or erase to the same Java signature (T001)
//   a nullable value used where a value is required (T015): `text.size()` on a `String?`, `n + 1`
//     on an `Integer?`, indexing or iterating a nullable array, a nullable condition or switch
//     subject, and `..=` on a `String?`; a null check narrows the binding first
//   `var x := 1` (P006), lowercase `null` (P006), a declaration without an initializer (P007),
//     and a repeated `?` suffix such as `String??` (P008)
//   a `try` statement with neither a `catch` nor a `finally`, a second `catch` clause, or a
//     `catch`/`finally` written on the line after `}` instead of the same line (P002/P001)
//   a `catch` clause whose type is not `Exception`, such as `catch (e: Integer)` (T016)
//   `return` inside a `finally` block (T017); `break`, `continue`, and `raise` are allowed there
//   `raise Null`, `raise 1`, raising a possibly-null `Exception?` (T001/T015), an `Exception`
//     construction that is not `(Integer, String)` (T001), and `error.code` where the language
//     has the functions `getCode()`/`getMessage()` (T001)
//   `type Exception`, or any declaration named `Exception` (T001); `try`, `catch`, `finally`, and
//     `raise` are keywords, so they are no longer identifier spellings
//   trait fields, constructors, static functions, generics, inheritance, or stateful defaults;
//     defaults named type/toString/equals/hashCode; two inherited defaults without an override
//   a constructor used outside its type, such as
//     solvik::util::UUID("0190f2a4-7b3c-7a1d-8e2f-3c4d5e6f7a8b", Long(0)) (T001)
//   `static` on a type or module, a static field without an initializer, a static member of a
//     generic type that names T, instance access to a static member, or type-name access to an
//     instance member (T001)
//
// Includes and aliases (SOL-I001..SOL-I004, SOL-T018):
//   `include { "missing.sol" }` (I001) and an absolute include path (I002)
//   top-level statements in an included file (I003): only the entry file runs statements
//   `include { "solvik::collection::Map" }`: an include names a file, and a packaged type is not
//     a file - it is already there (I001)
//   an include cycle between two files (I004)
//   `import "a.sol"` and `import { "a.sol" }` (P006), `include "a.sol"` and
//     `alias m::A A` without a block (P002), an empty block (P007), or a comma between
//     entries (P002)
//   an alias whose target is unknown, private to its module, a function, or missing (T018);
//     two aliases with one local name in a file (T018); an alias name that is a built-in type,
//     the reserved `solvik` root, or a top-level declaration (T018)
//   `module solvik { ... }` in a program file: the root namespace belongs to packaged resources
//     alone (T001)
//
// Runtime aborts (SOL-R001..SOL-R013), described rather than executed:
//   1 / zero (R001), 1 % zero (R002), BigDecimal("1") / BigDecimal("3") (R003), array[99] (R004),
//   a conversion of a runtime value that does not fit (R006), exit(value) outside 0..255 (R007),
//   a native member that returns Java null where its Solvik declaration promises a value (R008),
//   text.substring(99) (R009),
//   text.split(separator) with a runtime empty separator (R010), and a checked exception from a
//   native call (R011)
//   an Exception built with a negative code (R012), and a raised exception no clause handles (R013)
//   SOL-R005 is listed by LANGUAGE.md §13 as index overflow, but the current lowering never emits
//   it (JAVA_BACKEND.md records it as unreachable); an out-of-range index is SOL-R004.
//   A SOL-R abort, an `exit(t)`, and a Java exception from a native call are not catchable: only a
//   `raise`d Exception is (§7.4).
//
// Deliberate omissions (LANGUAGE.md §14): class inheritance, and therefore user-defined exception
// types or a second catchable type; an `Any` payload on Exception; trait state, inheritance, or
// generics; closures, function values, and futures; resizable language arrays; static type
// declarations or static top-level/module functions; generic static methods; binding Java fields,
// varargs, wildcards, or callbacks; automatic dependency download; and treating classpath
// isolation as a sandbox.
