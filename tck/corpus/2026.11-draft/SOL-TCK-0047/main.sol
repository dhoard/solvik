// Oracle derived from LANGUAGE_SPEC section 18. The verbatim normative sentence is:
//   "The compiler must narrow the type where the checked value is stable and no
//    intervening write can invalidate the refinement."
// Section 18 introduces its `is` and `as` forms under the labels "Support type tests:"
// and "Checked cast syntax:", each followed by a code block; those labels are section
// content rather than normative sentences, so they are not quoted as if they were rules.
//
// Expected bytes are derived by hand from the program's own text, not observed:
//   * `v` is declared with static type `Animal`, so `v.fetch()` is not readable without
//     refinement. `Animal` declares no `fetch` member, therefore the member read inside
//     `if (v is Dog)` compiles only if the compiler narrowed `v` to `Dog`, which section
//     18 requires for a stable checked value. The guarded block emits what Dog.fetch()
//     returns, `fetched`. (`v` is a `var`, so no intervening write can invalidate the
//     refinement, satisfying the same sentence's condition.)
//   * `v as Dog` on a Dog value is a successful cast; section 18 raises a Solvik runtime
//     type error only for an unsuccessful `as`, so execution continues and the last
//     statement emits `|cast-ok`.
// Total expected stdout: `fetched|cast-ok`. Uses print, so no platform line separator
// can enter the expected bytes.
class mutable Animal {
    method speak(): String {
        return "animal"
    }
}

class Dog extends Animal {
    method fetch(): String {
        return "fetched"
    }
}

var v: Animal = Dog()
if (v is Dog) {
    print(v.fetch())
}
var cast: Dog = v as Dog
print("|cast-ok")
