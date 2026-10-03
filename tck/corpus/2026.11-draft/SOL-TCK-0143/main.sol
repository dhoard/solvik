mutable class ParseError extends RuntimeException {
}
mutable class DeepError extends ParseError {
}
func guard() {
    try {
        throw DeepError("deep")
    } catch (e: Exception) {
        print(e.getMessage())
    }
}
guard() 
