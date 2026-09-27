// Oracle derived from LANGUAGE_SPEC section 20, which gives the expansion-order example
// verbatim:
//   "For example, when `root` includes `a` then `b`, and both `a` and `b` include
//    `common`, the expanded item order is the items of `common`, then the remaining items
//    of `a`, then the remaining items of `b`, then the remaining items of `root`."
// and the general rule it instantiates: "Expansion is depth-first and left-to-right."
// This program is that example written literally -- root includes a then b, and both a
// and b include common -- so the four printed pieces must appear in exactly the order the
// sentence enumerates. Section 20 also states that "The expanded executable top-level
// statements, in expansion order, form the one implicit `main`", which is what turns
// item order into output order.
// Expected bytes: `[common] [a] [b] [root] `. Each piece is bracketed and space-terminated
// so that the boundaries between the four contributions are observable: a bare
// concatenation such as `commonabroot` could also be produced by a different grouping of
// the same characters, which is why the delimiters are part of the fixture.
// Note `common` contributes once, not twice: its second include is a no-op under the
// canonical-file rule, so this test simultaneously pins order and expand-once. Where the
// specification itself defines an order (unlike `Map` position in section 11) an
// order-dependent oracle is the faithful one, and it is used here for that reason.
// Executed as top-level statements across file boundaries (section 20). Uses print.
include "lib/a.sol"
include "lib/b.sol"
print("[root] ")
