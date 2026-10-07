class mutable CodeError extends RuntimeException {
    var code: Integer

    CodeError(code: Integer) {
        this.code = code
    }
}
func guard() {
    try {
        throw CodeError(7, "sub message")
    }
    catch (e: CodeError) {
        print(e.code)
        print("[" .. e.getMessage() .. "]")
    }
}
guard()
