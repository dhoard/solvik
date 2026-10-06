// expected: SOLV-TYPE-030
// A generic function used as a value is instantiated from the expected function type in scope, so a
// reference with no expected type has nothing to instantiate to and is rejected on the reference
// (docs/LANGUAGE_SPEC.md section 6, "Generic function values": a generic function reference with no
// expected function type is SOLV-TYPE-030).
func identity<T>(value: T): T {
    return value
}

func demo(): Unit {
    var ambiguous = identity
}

print(demo)
