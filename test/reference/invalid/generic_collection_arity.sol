// expected C096: collections must be instantiated with their exact arity
package conformance

func main() -> int {
    xs: list<int, string> = [1, 2]
    m: map<int> = { 1: 2 }
    return 0
}
