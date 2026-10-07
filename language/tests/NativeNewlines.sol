// Demonstrates the \N platform-native newline escape (LANGUAGE_SPEC.md section 15).
// On the target runtime, \N expands to the native line separator;
// \n is always LF and \r\n is always CRLF.
var native: String = "\N"
var explicitLf: String = "\n"
var explicitCrlf: String = "\r\n"
var escapedBackslashN: String = "\\N"
var rawStringN: String = r#"raw \N"#
var mixed: String = "first\Nsecond\Nthird"
println(native)
println(explicitLf)
println(explicitCrlf)
println(escapedBackslashN)
println(rawStringN)
println(mixed)
