// Oracle derived from LANGUAGE_SPEC section 11, which states verbatim:
//   "`List<T>`, `Set<T>`, `Stack<T>`, and `Map<K, V>` are the initial built-in mutable
//    collection types. They are nominal generic types deriving from `Any`; their type
//    arguments are invariant and erased at run time."
// and, from the type hierarchy in section 4, `class Derived extends Base` makes `Derived`
// a nominal subtype of `Base`.
//
// `var box: List<Base> = items` assigns a value of type `List<Derived>` where
// `List<Base>` is declared. Because the type arguments are invariant, `List<Derived>` is
// not assignable to `List<Base>` even though `Derived` is a subtype of `Base`, so the
// assignment is a compile-time error.
// Why the assertion is confined to "rejected at compile time", with no code and no family:
// the quoted sentence states the invariance relation but names no stable code for the
// resulting diagnostic, and section 11 never says which analysis phase reports it.
// SOL-TCK-0090 is the required positive control: it differs from this program only in the
// declared type argument (`List<Base>` construction instead of a `List<Derived>` value in
// `List<Base>` position) and is accepted, so the rejection here cannot be caused by the
// `mutable`/`extends` declarations, by the constructor argument, or by `List` being unusable
// as a declared type. TCK.md section 6 forbids promoting the implementation's enum entry to
// normative status. Sentinel per TCK.md section 10: the print would be observable if this
// invalid assignment were accepted.
mutable class Base {
    func label(): String {
        return "base"
    }
}

class Derived extends Base {
}

var items = List<Derived>(Derived())
var box: List<Base> = items
print(box.size)
