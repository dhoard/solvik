package example
// ============================================================

//  example.sol -- Solvik Language Example
//  A complete executable demonstration of the Solvik language.
//
//  Run:  solvik run example.sol
// ============================================================

// ============================================================
//  1. Comments and Program Structure
// ============================================================

// Single-line comments use double-slash.
// Block comments (/* */) are not supported.

// Every Solvik source file starts with a package declaration.
// The package name is used for function mangling across modules.

// ============================================================
//  2. Variables and Primitive Types
// ============================================================

// Variables are declared with: name: Type = value
// Type annotations are required on declarations.
//
// Supported primitive types:
//   int, long, float, double, bool, char, byte, string
// Nullable types append ? to the type: string?

def demonstrateVariables() -> string {
    // Integer (32-bit signed)
    count: int = 42

    // Long (64-bit signed)
    bigNumber: long = 1000000

    // Boolean
    isActive: bool = true

    // Character
    initial: char = 'A'

    // Byte
    smallByte: int = 255

    // String
    greeting: string = "hello"

    // Null (nullable types use ? suffix)
    maybe: string? = null

    // Reassignment
    count = count + 1

    // String concatenation
    return greeting + " world count=" + string(count)
}

// ============================================================
//  3. Strings
// ============================================================

// Strings support standard escape sequences:
//   \n  newline, \t  tab, \\  backslash, \"  double quote
//
// Raw strings (Rust-style) preserve all characters literally:
//   r"..."        -- basic raw string
//   r#"..."#      -- raw string with one # delimiter
//   r##"..."##    -- raw string with two # delimiters
// Raw strings are especially useful for regex patterns and Windows paths.

def demonstrateStrings() -> string {
    // Ordinary string with escapes
    escaped: string = "line1\nline2\ttabbed"

    // Raw string -- backslashes are literal
    raw: string = r"C:\Users\name\file.txt"

    // Raw string with embedded quotes
    quoted: string = r#"The value is "quoted"."#

    return raw + " | " + quoted
}

// ============================================================
//  4. Operators and Expressions
// ============================================================

// Arithmetic:  +, -, *, /, %
// Comparison:  ==, !=, <, <=, >, >=
// Logical:     &&, ||, !
// Bitwise:     &, |, ^, ~, <<, >>
// String:      + (concatenation)
// Null:        ?? (null coalescing)
// Grouping:    ()

def demonstrateOperators() -> string {
    // Arithmetic
    sum: int = 10 + 20
    diff: int = 50 - 15
    product: int = 6 * 7
    quotient: int = 100 / 3
    remainder: int = 100 % 3

    // Comparison
    isEqual: bool = sum == 30
    isGreater: bool = diff > 30

    // Logical
    both: bool = isEqual && isGreater

    // String concatenation
    result: string = "sum=" + string(sum) + " product=" + string(product)

    // Null coalescing
    empty: string? = null
    fallback: string = empty ?? "default"

    // Bitwise
    bits: int = 255 & 15

    // Precedence
    computed: int = (10 + 20) * 2

    return result + " fallback=" + fallback
}

// ============================================================
//  5. Conditionals (if / else if / else)
// ============================================================

def demonstrateConditionals(value: int) -> string {
    if value > 0 {
        return "positive"
    } else if value < 0 {
        return "negative"
    } else {
        return "zero"
    }
    return "unknown"
}

// ============================================================
//  6. Switch Statements (Exact Matching)
// ============================================================

// Switch cases use first-match semantics -- no implicit fallthrough.
// A default clause is optional. Cases are checked in order.

def classifyStatusCode(code: int) -> string {
    switch code {
        case 200:
            return "OK"

        case 201:
            return "Created"

        case 204:
            return "No Content"

        case 400:
            return "Bad Request"

        case 404:
            return "Not Found"

        case 500:
            return "Internal Server Error"

        default:
            return "Unknown"
    }
}

// Switch also works with string values.

def classifyCommand(cmd: string) -> string {
    switch cmd {
        case "start":
            return "starting"

        case "stop":
            return "stopping"

        case "restart":
            return "restarting"

        default:
            return "unknown-command"
    }
}

// ============================================================
//  7. Switch with Regex Matching
// ============================================================

// The regex() built-in function compiles a regex pattern at runtime.
// When used in a case expression, the switch value is matched
// against the regex pattern using MatchString semantics.
// Raw strings (r"...") are the natural choice for regex patterns.

def classifyLogEntry(entry: string) -> string {
    switch entry {
        // Regex matching with raw strings -- backslashes are literal
        case regex(r"^ERROR\s+\[\d+\]:"):
            return "structured-error"

        case regex(r"^WARN\s+"):
            return "warning"

        case regex(r"^INFO\s+"):
            return "info"

        case regex(r"^DEBUG\s+"):
            return "debug"

        // Exact match uses == equality, checked in order before default
        case "UNKNOWN":
            return "unknown"

        default:
            return "unmatched"
    }
}

// The above patterns could also use ordinary strings with escaped backslashes:
//   regex("^ERROR\\s+\\[\\d+\\]:")   -- same pattern, escaped string

// Regex values are used inline in case expressions.
// Note: regex is not a declared type -- it is used only through the regex()
// built-in function in expressions.

// ============================================================
//  8. Loops
// ============================================================

// Supported loops: while, for-in (on lists).
// break and continue are supported inside loops.

// While loop

def sumUpTo(limit: int) -> int {
    total: int = 0
    current: int = 1
    while current <= limit {
        total = total + current
        current = current + 1
    }
    return total
}

// For-in loop on a list

def sumList(values: List<int>) -> int {
    total: int = 0
    for v in values {
        total = total + v
    }
    return total
}

// For-in with break

def firstEven(values: List<int>) -> int {
    for v in values {
        if v % 2 == 0 {
            return v
        }
    }
    return -1
}

// For-in with continue (skip negative values)

def sumPositive(values: List<int>) -> int {
    total: int = 0
    i: int = 0
    while i < len(values) {
        v: int = values[i]
        i = i + 1
        if v >= 0 {
            total = total + v
        }
    }
    return total
}

// While loop with continue

def skipMultiples(values: List<int>, skip: int) -> int {
    total: int = 0
    i: int = 0
    while i < len(values) {
        v: int = values[i]
        i = i + 1
        if v % skip != 0 {
            total = total + v
        }
    }
    return total
}

// ============================================================
//  9. Functions
// ============================================================

// Functions are declared with:
//   def name(params) -> ReturnType { body }
// Return type is required. Use void for no return value.

// Zero parameters

def greet() -> string {
    return "Hello, Solvik!"
}

// Multiple parameters

def formatMessage(level: string, message: string) -> string {
    return "[" + level + "] " + message
}

// Early return

def absolute(value: int) -> int {
    if value < 0 {
        return -value
    }
    return value
}

// Nested function calls

def formatGreeting(name: string, greeting: string) -> string {
    return greeting + ", " + name + "!"
}

// Recursion

def factorial(n: int) -> long {
    if n <= 1 {
        return 1
    }
    return n * factorial(n - 1)
}

// ============================================================
//  10. Collections (Lists and Maps)
// ============================================================

// Lists:  [value, value, ...]
// Maps:   {key: value, key: value, ...}

// List operations

def demonstrateLists() -> string {
    // List literal
    numbers: List<int> = [10, 20, 30, 40, 50]

    // Index access
    first: int = numbers[0]
    last: int = numbers[len(numbers) - 1]

    // Empty list
    empty: List<string> = []

    // List of strings
    names: List<string> = ["alice", "bob", "charlie"]

    return "first=" + string(first) + " last=" + string(last) + " count=" + string(len(numbers))
}

// Map operations

def demonstrateMaps() -> string {
    // Map literal: {key: value, key: value}
    config: Map<string, string> = {
        "host":   "localhost",
        "port":   "8080",
        "scheme": "http",
    }

    // Index access
    host: string = config["host"]

    return "host=" + host + " port=" + config["port"]
}

// List iteration with index access

def findValue(haystack: List<int>, needle: int) -> int {
    i: int = 0
    while i < len(haystack) {
        if haystack[i] == needle {
            return i
        }
        i = i + 1
    }
    return -1
}

// ============================================================
//  11. Trailing Commas in Call Arguments
// ============================================================

// A comma after the final argument is optional and does not
// create an extra argument. This improves multiline diffs.

def demonstrateTrailingCommas() -> string {
    // Single argument with trailing comma
    println("trailing-comma-1")

    // Multiline call with trailing comma
    msg: string = formatMessage(
        "DEBUG",
        "multiline trailing comma",
    )

    return msg
}

// ============================================================
//  12. Built-In Functions
// ============================================================

// Solvik provides built-in functions in namespaced modules.
// Many have unqualified aliases for convenience.

// 12a. Core functions (available unqualified)

def useCoreBuiltins() -> string {
    // print -- output a string
    println("using core builtins")

    // typeOf -- returns the type name as a string
    t1: string = typeOf(42)
    t2: string = typeOf("hello")
    t3: string = typeOf([1, 2, 3])
    t4: string = typeOf(null)
    t5: string = typeOf(true)

    // len -- returns the length of a list or map (not strings)
    listLen: int = len([10, 20, 30])

    // Conversions
    asString: string = string(42)
    asInt: int = int("123")
    asLong: long = long("456")
    asDouble: double = double("3.14")
    asBool: bool = bool(1)

    // regex -- compile a regex pattern (used inline in switch cases)

    return "listLen=" + string(listLen) + " typeOf=" + t1
}

// 12b. String module (use with string.length() etc.)

def useStringBuiltins() -> string {
    text: string = "Hello, World!"

    // Length (UTF-8 character count)
    length: int = string.length(text)

    // Byte length
    byteLen: int = string.byteLength(text)

    // Character at index
    first: char = string.charAt(text, 0)

    // Substring (start, end)
    sub: string = string.substring(text, 0, 5)

    // Contains
    hasWorld: bool = string.contains(text, "World")

    // Starts/Ends with
    startsHello: bool = string.startsWith(text, "Hello")
    endsWorld: bool = string.endsWith(text, "World!")

    // Index of
    pos: int = string.indexOf(text, ",")

    // Case conversion
    upper: string = string.toUpper("hello")
    lower: string = string.toLower("WORLD")

    // Trim whitespace
    trimmed: string = string.trim("  spaced  ")

    // Split
    parts: List<string> = string.split("a,b,c", ",")

    // Join
    joined: string = string.join(parts, "-")

    return joined + " sub=" + sub + " upper=" + upper
}

// 12c. Math module (use with math.abs() etc.)

def useMathBuiltins() -> string {
    absolute: double = math.abs(-42.5)
    minimum: double = math.min(10.5, 20.3)
    maximum: double = math.max(10.5, 20.3)
    floorVal: double = math.floor(3.7)
    ceilVal: double = math.ceil(3.2)
    rounded: double = math.round(3.5)
    sqrtVal: double = math.sqrt(64.0)
    powVal: double = math.pow(2.0, 8.0)

    return "sqrt=" + string(sqrtVal) + " pow=" + string(powVal)
}

// 12d. Environment module

def useEnvBuiltins() -> string {
    // Read an environment variable (returns null if not set)
    home: string? = env.get("HOME")
    fallback: string = home ?? "/tmp"

    return "home=" + fallback
}

// 12e. File module (safe read-only operations)

def useFileBuiltins() -> string {
    // Check if file exists
    exists: bool = file.exists("example.sol")

    if exists {
        // Read file content (we read our own source -- non-destructive)
        content: string = file.read("example.sol")
        return "exists=true fileSize=" + string(string.length(content))
    }

    return "exists=false"
}

// 12f. Process module (safe command execution)

def useProcessBuiltin() -> string {
    // Run an external command and capture its exit code
    exitCode: int = process.run("/bin/echo")

    return "exitCode=" + string(exitCode)
}

// 12g. Time module

def useTimeBuiltins() -> string {
    // Current time in milliseconds since Unix epoch (UTC)
    now: long = time.now()

    // Note: time.now() returns a long representing milliseconds.
    // To get seconds: now / 1000

    return "now_ms=" + string(now)
}

// ============================================================
//  13. Statement Termination
// ============================================================

// Statements are terminated by newlines (the idiomatic form)
// or semicolons (for multiple statements on one line).

def demonstrateTermination() -> string {
    // Each statement on its own line (terminated by newline)
    a: int = 1
    b: int = 2

    // Semicolons allow multiple statements on one line
    c: int = 3; d: int = 4

    return string(a + b + c + d)
}

// ============================================================
//  14. Nested Expressions
// ============================================================

// Function calls can be nested inside other calls.
// Complex expressions work with all operators and parentheses.

def evaluateExpression(x: int, y: int) -> int {
    return (x * y) + (x - y) / 2
}

// Conditional value via early return pattern

def maxValue(a: int, b: int) -> int {
    if a > b {
        return a
    }
    return b
}

// ============================================================
//  15. Block Scope
// ============================================================

// Variables can be scoped within blocks.

def demonstrateScope() -> string {
    x: int = 5

    // Inner block with its own variable
    {
        x: int = 10
        println("  inner x=" + string(x))
    }

    // Outer variable is unchanged
    return "outer x=" + string(x)
}

// ============================================================
//  16. Void Functions
// ============================================================

// Functions with no return value use void as the return type.

def printSeparator() -> void {
    println("----------------------")
}

// ============================================================
//  17. Main Entry Point
// ============================================================

// The main() function is the program entry point.
// It must return int. Return 0 for success.

def main() -> int {
    println("=== Solvik Language Example ===")

    // ---- Section 2: Variables and Primitive Types ----
    println("=== 2. Variables ===")
    vars: string = demonstrateVariables()
    println("  " + vars)

    // ---- Section 3: Strings ----
    println("=== 3. Strings ===")
    strResult: string = demonstrateStrings()
    println("  " + strResult)

    // ---- Section 4: Operators ----
    println("=== 4. Operators ===")
    opResult: string = demonstrateOperators()
    println("  " + opResult)

    // ---- Section 5: Conditionals ----
    println("=== 5. Conditionals ===")
    condPos: string = demonstrateConditionals(42)
    condNeg: string = demonstrateConditionals(-5)
    condZero: string = demonstrateConditionals(0)
    println("  42 -> " + condPos)
    println("  -5 -> " + condNeg)
    println("  0  -> " + condZero)
    println("")

    // ---- Section 6: Switch (Exact Matching) ----
    println("=== 6. Switch (Exact) ===")
    println("  200 -> " + classifyStatusCode(200))
    println("  404 -> " + classifyStatusCode(404))
    println("  999 -> " + classifyStatusCode(999))
    println("  start -> " + classifyCommand("start"))
    println("  unknown -> " + classifyCommand("unknown"))
    println("")

    // ---- Section 7: Switch (Regex Matching) ----
    println("=== 7. Switch (Regex) ===")
    println("  ERROR [123]: fail -> " + classifyLogEntry("ERROR [123]: fail"))
    println("  WARN  disk full -> " + classifyLogEntry("WARN  disk full"))
    println("  plain text -> " + classifyLogEntry("plain text"))
    println("  UNKNOWN -> " + classifyLogEntry("UNKNOWN"))
    println("")

    // ---- Section 8: Loops ----
    println("=== 8. Loops ===")
    total: int = sumUpTo(10)
    println("  sumUpTo(10) = " + string(total))

    listSum: int = sumList([1, 2, 3, 4, 5])
    println("  sumList = " + string(listSum))

    first: int = firstEven([1, 3, 5, 8, 11])
    println("  firstEven = " + string(first))

    posSum: int = sumPositive([10, -5, 20, -8, 30])
    println("  sumPositive = " + string(posSum))

    skipped: int = skipMultiples([1, 2, 3, 4, 5, 6, 7, 8], 3)
    println("  skipMultiples(3) = " + string(skipped))
    println("")

    // ---- Section 9: Functions ----
    println("=== 9. Functions ===")
    println("  " + greet())
    println("  " + formatMessage("WARN", "disk space low"))
    println("  absolute(-7) = " + string(absolute(-7)))
    println("  " + formatGreeting("Alice", "Good morning"))
    println("  factorial(10) = " + string(factorial(10)))
    println("")

    // ---- Section 10: Collections ----
    println("=== 10. Collections ===")
    listDemo: string = demonstrateLists()
    println("  " + listDemo)

    mapDemo: string = demonstrateMaps()
    println("  " + mapDemo)

    foundIdx: int = findValue([5, 10, 15, 20, 25], 15)
    println("  findValue(15) at index " + string(foundIdx))
    println("")

    // ---- Section 11: Trailing Commas ----
    println("=== 11. Trailing Commas ===")
    trailResult: string = demonstrateTrailingCommas()
    println("  " + trailResult)
    println("")

    // ---- Section 12: Built-ins ----
    println("=== 12. Built-in Functions ===")
    println("  " + useCoreBuiltins())
    println("  " + useStringBuiltins())
    println("  " + useMathBuiltins())
    println("  " + useEnvBuiltins())
    println("  " + useFileBuiltins())
    println("  " + useProcessBuiltin())
    println("  " + useTimeBuiltins())
    println("")

    // ---- Section 13: Statement Termination ----
    println("=== 13. Statement Termination ===")
    termResult: string = demonstrateTermination()
    println("  result=" + termResult)
    println("")

    // ---- Section 14: Expressions ----
    println("=== 14. Expressions ===")
    exprResult: int = evaluateExpression(10, 4)
    println("  evaluateExpression(10, 4) = " + string(exprResult))
    maxVal: int = maxValue(15, 8)
    println("  maxValue(15, 8) = " + string(maxVal))
    println("")

    // ---- Section 15: Block Scope ----
    println("=== 15. Block Scope ===")
    scopeResult: string = demonstrateScope()
    println("  " + scopeResult)
    println("")

    // ---- Summary ----
    printSeparator()
    println("=== Example completed successfully ===")
    return 0
}
