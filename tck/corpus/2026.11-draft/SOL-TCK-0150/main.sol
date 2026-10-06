mutable class ParseError extends RuntimeException {
}
func guard() {
    try {
        throw ParseError("via base")
    }
    catch (e: RuntimeException) {
        print("[" .. e.getMessage() .. "]")
    }
}
guard()
