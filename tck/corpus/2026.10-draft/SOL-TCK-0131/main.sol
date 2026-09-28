// Positive conformance test. Oracle derived by hand from LANGUAGE_SPEC section 21.5:
// "In expression position the same surface syntax produces a value", with the spec's
// `case Status.Ready: "ready" ... default: "done"` shape; and "a statement `switch` may
// still omit `default` and do nothing when no label matches".
// Both halves are exercised. The expression form over the Integer 2 selects "two", and the
// statement form is given a label list that matches nothing, so it must contribute no
// bytes at all. Expected stdout is therefore exactly "[two]" -- an implicit fallthrough, a
// synthesized default, or a statement switch that complained would each change it. The
// brackets are this test's own addition: a statement switch elsewhere in the corpus
// already derives the bare bytes "two" from the statement-form rules, and sharing those
// exact bytes would make the two expectations mutually uncheckable.
val n = 2
val word = switch (n) {
    case 1:
        "one"
    case 2:
        "two"
    default:
        "other"
}
switch (n) {
    case 99:
        print("MUST-NOT-APPEAR")
}
print("[")
print(word)
print("]")
