// expected C109: canonical built-in type names cannot be shadowed
package reference_invalid

struct Int {}

func main() -> Int { return 0 }
