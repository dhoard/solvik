// Solvik TCK SOL-TCK-0435
// `Left` and `Right` declare the same property name and type and the same method and remain assignment-incompatible, which is the boundary on structural comparison as a rule that belongs to function types alone.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - Two function types are identical when they have the same number of parameters, corresponding parameter types are identical, and their return types are identical. The declarations that produced values of those types do not affect type identity.
//   - Structural comparison is confined to function types: two unrelated classes with identical members remain assignment-incompatible (section 3).
//   - Semantic equality for function values is reference identity, and `hashCode()` is the matching reference-identity hash. These operations are fixed and cannot be overridden.
//   - A static declaration initializer that is not assignable to the declared type is `SOLV-TYPE-001`.
//
func format(value: Integer): String {
    return "v" .. value.toString()
}

class Left {
    val name: String = "left"

    func value(): Integer {
        return 1
    }
}

class Right {
    val name: String = "left"

    func value(): Integer {
        return 1
    }
}

// Structural comparison is confined to function types. `Left` and `Right` declare the same
// property name and type and the same method, and remain assignment-incompatible; that
// nominal half is the non-assignable static initializer, which the section pins.
class Boundary {
    static val copied: Right = Left()
}

print(format(1))
print("EXECUTED-INVALID")
