// Positive lexical conformance test: the complete set of normal-string escapes.
// Oracle derived by hand from LANGUAGE_SPEC section 15, verbatim: "Normal strings
// cannot contain an unescaped physical newline. They support exactly `\\`, `\"`, `\n`,
// `\r`, `\t`, and `\0`. Any other escape is a lexical error."
// The source writes the six supported escapes in order, so the value is exactly:
//   backslash, double quote, newline, carriage return, tab, NUL
// flanked by the literal letters a..g. `print` appends no separator (section 5), so
// expected stdout is those 13 bytes:
//   61 5C 62 22 63 0A 64 0D 65 09 66 00 67
// Each byte is determined by the quoted escape list alone; nothing was read from the IUT.
var s = "a\\b\"c\nd\re\tf\0g"
print(s)
