class mutable ParseError extends RuntimeException {
}
class mutable DeepError extends ParseError {
}
func guard() {
    try {
        throw DeepError("deep")
    }
    catch (e: Exception) {
        print(e.getMessage())
    }
}
guard() 
