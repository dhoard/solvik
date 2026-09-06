package example
func main() -> Int {
    values: List<Int> = [10, 20]
    mut total: Int = 0
    for value in values {
        total = total + value
    }
    print("Total: " .. total .. "\n")
    return 0
}
