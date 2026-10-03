// Oracle derived from LANGUAGE_SPEC section 20, which states verbatim:
//   "`include P alias p` binds the prefix `p` to the included file's module instead."
// and: "The included declarations are reached through the prefix with the `::` namespace
// separator" -- the same qualified form the section shows for a class
// (`val point: math::Point = math::Point(1)`).
//
// Expected bytes derived by hand from the program text:
//   * `g::Point(6)` invokes the class constructor, whose parameter is written `v:
//     Integer` and assigns the argument to `x` unchanged; `print(p.x)` emits `6`;
//   * the `print(" ")` between them emits one space;
//   * `g::scale(3)` returns 3 * 2 under section 3's arithmetic rules, so it emits `6`.
// Total expected stdout: `6 6`.
// The alias `g` is written once and used for both a class and a function, exercising the
// claim that the prefix addresses the aliased file's whole module, not one declaration.
// Executed as top-level statements (section 20). Uses print, so no platform line
// separator can enter the expected bytes.
include "lib/m.sol" alias g

val p: g::Point = g::Point(6)
print(p.x)
print(" ")
print(g::scale(3))
