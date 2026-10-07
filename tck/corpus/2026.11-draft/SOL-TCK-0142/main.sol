class mutable ParseError extends RuntimeException {
}
func guard() {
    try {
        throw ParseError("bad int")
    }
    catch (e: Exception) {
        print("handled")
        print(e.getMessage())
    }
}
guard()
