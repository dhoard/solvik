package example
func main() -> int {
    values: list<int> = [10, 20]
    mut total: int = 0
    for value in values {
        total = total + value
    }
    print("Total: " .. total .. "\n")
    return 0
}
