// Solvik TCK SOL-TCK-0405
// An include inside a function body is not a compilation-unit item.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - An include may appear only as an item of a compilation unit: it is not a statement and cannot appear in a function, method, constructor, block, loop, switch, or match branch.
//
func f() {
    include "x.sol"
}
f()
print("EXECUTED-INVALID")
