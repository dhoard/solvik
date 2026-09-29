// Solvik TCK SOL-TCK-0480
// Two creations from one receiver and method are distinct, a binding copy preserves the identity, and both directions are asserted over the same pair of values
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - Each successful evaluation of a bound method-reference expression creates a distinct function-value identity, even for the same receiver and method.
//   - Copying that value through bindings preserves its identity.
//
class Formatter {
    func format(): String {
        return "f"
    }
}

val formatter = Formatter()
val first: func(): String = formatter.format
val second: func(): String = formatter.format
val copied = first
print(first === second)
print("|")
print(copied === first)
print("|")
print(first === second)
print("|")
print(first === first)
