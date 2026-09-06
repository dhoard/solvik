package native_try

func main() -> Int {
    mut result: Int = 0
    try {
        x: Int = 10
        y: Int = 0
        result = x / y
    } catch (error: Exception) {
        if error.code == "E031" {
            result = 41
        }
    } finally {
        result = result + 1
    }
    return result - 42
}
