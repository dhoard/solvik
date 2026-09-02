// Switch cases must be assignable to the switch type; regex and null-on-nullable are exempt.
package conformance

func main() -> int {
    code: int = 200
    switch code {
        case 200 { }
        default { }
    }
    f: float = 1.0
    switch f {
        case 1 { }
        default { }
    }
    s: string = "ERROR [1]: x"
    switch s {
        case regex(r"^ERROR") { }
        default { }
    }
    n: string? = null
    switch n {
        case null { }
        case "a" { }
        default { }
    }
    return 0
}
