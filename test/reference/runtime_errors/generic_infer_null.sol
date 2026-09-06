// expected E067: a null value alone cannot infer a type parameter
package runtime_errors

func identity<T>(value: T) -> T {
    return value
}

func main() -> Int {
    r: Any = identity(null)
    return 0
}
