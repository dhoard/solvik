// Oracle derived from LANGUAGE_SPEC section 20, which states verbatim:
//   "A module declaration contributes its name to the whole program: after expansion,
//    `Name::member` names the declaration of a `module Name { ... }` block from any file
//    of the program."
// and: "Included declarations are reached through that name with the `::` namespace
// separator:" -- the same qualified form the section shows for a class
// (`var point: math::Point = math::Point(1)`).
//
// Expected bytes derived by hand from the program text:
//   * `geom::Point(6)` invokes the class constructor, whose parameter is written `v:
//     Integer` and assigns the argument to `x` unchanged; `print(p.x)` emits `6`;
//   * the `print(" ")` between them emits one space;
//   * `geom::scale(3)` returns 3 * 2 under section 3's arithmetic rules, so it emits `6`.
// Total expected stdout: `6 6`.
// The module name `geom` is written once and used for both a class and a function,
// exercising the claim that the name addresses the module's whole contents, not one
// declaration.
// Executed as top-level statements (section 20). Uses print, so no platform line
// separator can enter the expected bytes.
include "lib/m.sol"

var p: geom::Point = geom::Point(6)
print(p.x)
print(" ")
print(geom::scale(3))
