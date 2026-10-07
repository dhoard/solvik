// Solvik TCK SOL-TCK-0369
// The static member may use the reserved instance name and the class-name read observes its value.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - The `toString`/`equals`/`hashCode` reserved-name rules apply to **instance** members only, so a static member may use those names.
//
class C {
    var static toString: Integer = 7

    C() {
    }
}
print(C.toString)
