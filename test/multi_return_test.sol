package test

func divide(a: int, b: int) -> int, int {
    return a / b, a % b
}

func main() -> int {
    mut q: int
    mut r: int
    q, r = divide(10, 3)
    println(string(q))
    println(string(r))
    println("done")
    return 0
}
