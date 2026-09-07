package mp_rethrow
func inner() -> Int {
    throw "inner"
}
func main() -> Int {
    try {
        inner()
    } catch (e: Exception) {
        throw e
    }
    return 0
}
