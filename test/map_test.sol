package example

func main() -> int {
    scores: Map<string, int> = {"alice": 100, "bob": 200}
    mut total: int = 0
    for name in scores {
        total = total + scores[name]
    }
    if total != 300 {
        return 1
    }
    if !map.contains(scores, "alice") {
        return 2
    }
    if map.contains(scores, "charlie") {
        return 3
    }
    return 0
}
