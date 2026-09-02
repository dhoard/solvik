// expected E068: closure called with the wrong argument count
package runtime_errors

func main() -> int {
    anything: any = func(x: int) -> int { return x + 1 }
    anything(1, 2)
    return 0
}
