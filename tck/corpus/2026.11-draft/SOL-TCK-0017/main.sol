// Positive lexical conformance test: Rust-style raw string delimiters.
// Oracle derived by hand from LANGUAGE_SPEC section 15, which gives the general rule
// "r + N '#' characters + '\"' + content + '\"' + exactly N '#' characters" and states
// "The token's semantic value is the content between the delimiters" and that raw
// strings "do not process backslash escapes".
//   * r#"..."# (N=1) with a backslash inside: because escapes are not processed, the
//     two characters `C` and `\` and `t` stay exactly as written, so the JSON text is
//     reproduced literally, backslash included.
//   * r###"..."### (N=3): the section states "the first quote followed by exactly N
//     hashes closes the token", so the embedded `"#` (a quote followed by ONE hash, not
//     three) does NOT close the token and is part of the value.
// Expected stdout is therefore the literal bytes of both values, `print` appending no
// line separator (section 5). This is a spec derivation, not a capture from the IUT.
var json: String = r#"{"name":"Doug","path":"C:\temp"}"#
print(json)
var hashes: String = r###"arbitrary "# content"###
print(hashes)
