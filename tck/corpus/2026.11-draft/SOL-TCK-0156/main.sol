class mutable ParseError extends RuntimeException {
}
class mutable SubError extends ParseError {
}
func guard() {
    try {
        throw SubError("s")
    }
    catch (e: SubError) {
        print("specific")
    }
    catch (e: RuntimeException) {
        print("base")
    }
}
guard()
