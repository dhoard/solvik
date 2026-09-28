// Oracle derived by hand from LANGUAGE_SPEC section 5, verbatim:
//   "The predeclared `exit(code: Integer)` function runs no further Solvik code: it
//    terminates the program with `code` as the process exit status and returns no value."
// Obligation (section 7): an explicit exit(n) is a *normal* language completion with a
// declared language exit status, NOT a runtime error, even though n is nonzero. print
// before exit produces exact stdout "hi" with no separator.
print("hi")
exit(7)
print("unreachable")
