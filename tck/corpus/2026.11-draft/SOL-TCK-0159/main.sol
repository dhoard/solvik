class mutable ParseError extends RuntimeException {
}
class mutable OtherError extends RuntimeException {
}
func guard() {
    try {
        throw ParseError("one")
    }
    catch (e: ParseError) {
        print("[" .. e.getMessage() .. "]")
    }
    catch (e: OtherError) {
        print("[" .. e.getMessage() .. "]")
    }
}
guard()
print("done")
