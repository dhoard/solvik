package example

struct Point {
    x: Int,
    y: Int,
}

func main() -> Int {
    // Nullable struct
    mut maybe: Point? = null
    println("maybe is null")

    // Assign a value
    maybe = Point { x: 10, y: 20 }

    if maybe != null {
        println("maybe is not null")
    }

    println("nullable struct test passed")
    return 0
}
