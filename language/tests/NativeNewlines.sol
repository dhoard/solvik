// Demonstrates the \N platform-native newline escape (LANGUAGE_SPEC.md section 15).
// On the target runtime, \N expands to the native line separator;
// \n is always LF and \r\n is always CRLF.
var native = "\N"
var explicitLf = "\n"
var explicitCrlf = "\r\n"
var escapedBackslashN = "\\N"
var rawStringN = r#"raw \N"#
var mixed = "first\Nsecond\Nthird"
println(native)
println(explicitLf)
println(explicitCrlf)
println(escapedBackslashN)
println(rawStringN)
println(mixed)
