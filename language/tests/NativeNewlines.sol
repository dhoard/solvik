// Demonstrates the \N platform-native newline escape (LANGUAGE_SPEC.md section 15).
// On the target runtime, \N expands to the native line separator;
// \n is always LF and \r\n is always CRLF.
val native = "\N"
val explicitLf = "\n"
val explicitCrlf = "\r\n"
val escapedBackslashN = "\\N"
val rawStringN = r#"raw \N"#
val mixed = "first\Nsecond\Nthird"
println(native)
println(explicitLf)
println(explicitCrlf)
println(escapedBackslashN)
println(rawStringN)
println(mixed)
