// Positive control for the type-argument invariance rule tested by SOL-TCK-0091.
// Oracle derived from LANGUAGE_SPEC section 11, which states verbatim:
//   "`List<T>`, `Set<T>`, `Stack<T>`, and `Map<K, V>` are the initial built-in mutable
//    collection types. They are nominal generic types deriving from `Any`; their type
//    arguments are invariant and erased at run time."
//
// This program supplies one `Derived` value to a construction whose declared element type
// is `Base`. `class Derived extends Base` is a nominal subtype relation, so the value
// argument satisfies section 11's requirement that each initial element "must be
// assignable to the element type", and the two type arguments written are identical, which
// invariance always permits. The expected bytes follow from section 11's table entry
// `val size: Integer` for a list built from one element, which makes size 1, and from
// section 3's `..`, which concatenates its operands: `"base="` followed by `1` is
// `base=1`.
// Why this control is required: without it, a rejection of SOL-TCK-0091 could be caused by
// any of several unrelated faults -- for example a declaration whose element type is
// broken in general, or a constructor that rejects the declared type outright -- and
// SOL-TCK-0091 would appear to demonstrate invariance while proving nothing. Together the
// pair isolates the single difference between them, the declared type argument.
// Executed as top-level statements (section 20). Uses print, so no platform line separator
// can enter the expected bytes.
mutable class Base {
    func label(): String {
        return "base"
    }
}

class Derived extends Base {
}

val items = List<Base>(Derived())
print("base=" .. items.size)
