// Switch cases must be assignable to the switch type; regex and null-on-nullable are exempt.
package conformance

func main() -> Int {
    code: Int = 200
    switch code {
        case 200 { }
        default { }
    }
    f: Float = 1.0
    switch f {
        case 1 { }
        default { }
    }
    s: String = "ERROR [1]: x"
    switch s {
        case Regex.new(r"^ERROR") { }
        default { }
    }
    n: String? = null
    switch n {
        case null { }
        case "a" { }
        default { }
    }
    return 0
}
