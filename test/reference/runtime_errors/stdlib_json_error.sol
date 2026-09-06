// expected E072: malformed JSON is a standard-library error
package runtime_errors

func main() -> Int {
    x: Any = json.parse("{not json}")
    println(x)
    return 0
}
