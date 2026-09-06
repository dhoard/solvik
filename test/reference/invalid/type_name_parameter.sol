// expected C099: canonical built-in type names cannot be shadowed
package reference_invalid

func identity<Int>(value: Int) -> Int { return value }

func main() -> Int { return 0 }
