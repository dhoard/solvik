package example

func main() -> int {
    values: list<int> = [10, 20, 30, 40, 50]
    mut total: int = 0
    for v in values {
        total = total + v
    }
    if total != 150 {
        return 1
    }
    return 0
}
