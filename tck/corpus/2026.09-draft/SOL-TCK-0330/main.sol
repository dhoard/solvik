// Solvik TCK SOL-TCK-0330
// An uninitialized property without an explicit constructor removes the implicit zero-arg initializer.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - A class with no explicit constructor has an implicit zero-argument initializer only when all properties have declaration initializers.
//
class User {
    var name: String
}
val u = User()
print("EXECUTED-INVALID")
