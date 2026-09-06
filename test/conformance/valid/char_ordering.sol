// Characters order by Unicode code point.
package conformance

func main() -> Int {
    if 'a' < 'b' && 'z' > 'a' && !('z' > 'é') && 'A' <= 'a' {
        return 1
    }
    return 0
}
