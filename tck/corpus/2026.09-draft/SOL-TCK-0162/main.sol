open class ParseError extends RuntimeException {
}
func guarded() {
    try {
        throw ParseError("p")
    } finally {
        print("released")
    }
}
func driver() {
    try {
        guarded()
    } catch (e: Exception) {
        print("handled")
        print("[" .. e.getMessage() .. "]")
    }
}
driver()
