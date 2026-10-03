mutable class ParseError extends RuntimeException {
}
func guard() {
    try {
        print("body")
    } finally {
        print("fin")
    }
}
guard()
print("after")
