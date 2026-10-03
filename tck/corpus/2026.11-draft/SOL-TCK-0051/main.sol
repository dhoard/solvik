// Oracle derived by hand from LANGUAGE_SPEC section 6, verbatim:
//   "Both accept every value including `null`; `null` displays as `null`."
//   "Boolean values as `true` or `false`"
// Obligation: `print` accepts `null` (its parameter is `Any?`) and renders the four
// letters n-u-l-l, and the two Boolean literals render as their lower-case words.
// `print` appends no separator, so the three renderings concatenate into one byte-exact
// stream.
print(null)
print(true)
print(false)
