// expect: P075
package conformance

func main() -> int {
    values: map<string, int> = { "answer": 42 }
    for (key, value) in values {
        println(key .. value)
    }
    return 0
}
