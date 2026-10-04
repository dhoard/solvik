mutable class ParseError extends RuntimeException {
}
func inner() {
    throw ParseError("two frames down")
}
func middle() {
    inner()
}
func driver() {
    try {
        middle()
    }
    catch (e: Exception) {
        print("handled")
        print("[" .. e.getMessage() .. "]")
    }
}
driver()
