package example
func main() -> int {
    values: List<int> = [10, 20]
    mut total: int = 0
    for value in values {
        total = total + value
    }
    print("Total: " .. total .. "\n")
    return 0
}
