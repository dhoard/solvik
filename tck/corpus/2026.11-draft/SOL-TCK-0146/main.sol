class mutable ParseError extends RuntimeException {
}
func guard() {
    try {
        throw ParseError("m")
    }
    catch (e: ParseError) {
        print(e.message)
    }
}
guard()

print("EXECUTED-INVALID")
