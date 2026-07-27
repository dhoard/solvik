package example

def main() -> int {
    values: List<int> = [10, 20, 30, 40, 50]
    total: int = 0
    for v in values {
        total = total + v
    }
    if total != 150 {
        return 1
    }
    return 0
}
