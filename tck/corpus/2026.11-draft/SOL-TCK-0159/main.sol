mutable class ParseError extends RuntimeException {
}
mutable class OtherError extends RuntimeException {
}
func guard() {
    try {
        throw ParseError("one")
    } catch (e: ParseError) {
        print("[" .. e.getMessage() .. "]")
    } catch (e: OtherError) {
        print("[" .. e.getMessage() .. "]")
    }
}
guard()
print("done")
