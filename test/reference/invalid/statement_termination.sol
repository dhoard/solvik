// expected P078: adjacent statements need a newline or semicolon
package reference_invalid

func main() -> int {
    x: int = 5 y: int = 6
    return x
}
