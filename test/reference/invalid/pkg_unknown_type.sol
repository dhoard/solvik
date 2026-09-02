// expected C110: qualified names must resolve to a known type
package reference_invalid

func main() -> int {
    x: nosuch.Client = 5
    return 0
}
