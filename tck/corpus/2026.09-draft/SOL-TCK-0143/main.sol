open class ParseError extends RuntimeException {
}
open class DeepError extends ParseError {
}
func guard() {
    try {
        throw DeepError("deep")
    } catch (e: Exception) {
        print(e.getMessage())
    }
}
guard() 
