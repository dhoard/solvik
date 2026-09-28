open class ParseError extends RuntimeException {
}
func guard() {
    try {
        try {
            throw ParseError("kept")
        } catch (e: ParseError) {
            throw e
        }
    } catch (again: ParseError) {
        print("outer")
        print("[" .. again.getMessage() .. "]")
    }
}
guard()
