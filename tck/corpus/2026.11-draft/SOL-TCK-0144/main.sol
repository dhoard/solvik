class mutable ParseError extends RuntimeException {
}
func guard() {
    try {
        throw ParseError()
    }
    catch (e: ParseError) {
        print("[" .. e.getMessage() .. "]")
    }
}
guard()
