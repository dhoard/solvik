package example
def main() -> int {
    values: List<int> = [10, 20]
    total: long = 0
    for value in values {
        total = total + value
    }
    print("Total: " + string(total) + "\n")
    return 0
}
