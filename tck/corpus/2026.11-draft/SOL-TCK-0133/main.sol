// Positive conformance test. Oracle derived by hand from LANGUAGE_SPEC section 21.5,
// verbatim: "The scrutinee is evaluated exactly once. Case labels are tested in source
// order, only the first matching body executes, and there is no implicit fallthrough."
// The scrutinee is a call whose only effect is to emit an observation marker, so "exactly
// once" becomes a byte-exact claim: the marker must appear exactly once, and the matched
// body's value follows it. Expected stdout is exactly "Sone".
// Re-evaluating the scrutinee for a second label comparison would emit "SSone", and
// evaluating it once per tested label would emit more markers still.
class Probe {
    func tick(): String {
        print("S")
        return "one"
    }
}
val result = switch (Probe().tick()) {
    case "one" {
        "one"
    }
    case "two" {
        "two"
    }
    default {
        "other"
    }
}
print(result)
