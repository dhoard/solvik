mutable class ParseError extends RuntimeException {
}
func guard() {
    try {
        throw ParseError("x")
    }
    catch (e: ParseError) {
        print("caught")
    }
    print(e)
}
guard()

print("EXECUTED-INVALID")
