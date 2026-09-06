package example

func main() -> Int {
    values: List<Int> = [10, 20, 30, 40, 50]
    mut total: Int = 0
    for v in values {
        total = total + v
    }
    if total != 150 {
        return 1
    }
    return 0
}
