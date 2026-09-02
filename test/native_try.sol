package native_try

func main() -> int {
    mut result: int = 0
    try {
        x: int = 10
        y: int = 0
        result = x / y
    } catch (error: exception) {
        if error.code == "E031" {
            result = 41
        }
    } finally {
        result = result + 1
    }
    return result - 42
}
