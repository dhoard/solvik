// Solvik TCK SOL-TCK-0388
// The write to x invalidates the is-refinement, so the later String read is rejected.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - The compiler must narrow the type where the checked value is stable and no intervening write can invalidate the refinement.
//
func f(v: Any): Integer {
    mutable val x: Any = v
    if (x is String) {
        x = 1
        val s: String = x
        return 1
    }
    return 0
}
print("EXECUTED-INVALID")
