// Solvik TCK SOL-TCK-0362
// One value of each named built-in renders through its fixed toString; the separators name which token belongs to which type.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - Built-in scalars provide fixed, non-overridable implementations: `Integer`, `Long`, `Byte`, and `Short` render in decimal, `Float` and `Double` use Java-style floating-point text, `Boolean` renders `true` or `false`, `Character` renders its character, `String` renders its contents, and `Unit` renders `Unit`.
//
var i: Integer = 42
var l: Long = 42L
var f: Float = 1.5F
var d: Double = 1.5
var c: Character = 'A'
var s: String = "hi"
var b: Boolean = true
print(i)
print("|")
print(l)
print("|")
print(f)
print("|")
print(d)
print("|")
print(c)
print("|")
print(s)
print("|")
print(b)
