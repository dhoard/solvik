// Positive lexical conformance test: raw strings preserve embedded newlines.
// Oracle derived by hand from LANGUAGE_SPEC section 15, whose raw-string bullet list
// states they "preserve embedded newlines", demonstrated by the section's own `sql`
// example whose delimiter is followed by a newline on its own line.
// The value is the content between the delimiters, and that content begins with the
// newline immediately after r#" and ends with the newline immediately before the
// closing "#. `print` appends no line separator (section 5), so the expected stdout is
// exactly a leading newline, the two SQL lines each terminated by a newline.
var sql: String = r#"
SELECT *
FROM users
"#
print(sql)
