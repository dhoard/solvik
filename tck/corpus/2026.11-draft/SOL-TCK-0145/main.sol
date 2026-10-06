mutable class ParseError extends RuntimeException {
}
func guard() {
    try {
        throw ParseError("bad int")
    }
    catch (e: ParseError) {
        print("[" .. e.getMessage() .. "]")
    }
}
guard()
