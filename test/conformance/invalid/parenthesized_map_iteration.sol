// expect: P075
package conformance

func main() -> Int {
    values: Map<String, Int> = { "answer": 42 }
    for (key, value) in values {
        println(key .. value)
    }
    return 0
}
