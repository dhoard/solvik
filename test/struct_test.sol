package example

struct Point {
    pub x: int,
    pub y: int,
}

struct Empty {}

struct Config {
    pub mut host: string,
    pub mut port: int,
    pub timeout: int,
}

func main() -> int {
    // Positional construction
    p: Point = Point(3, 4)
    println("Point: " .. p.x .. ", " .. p.y)

    // Field access
    sum: int = p.x + p.y
    println("Sum: " .. sum)

    // Empty struct
    e: Empty = Empty()
    println("Empty created")

    // Struct with mutable fields
    cfg: Config = Config("localhost", 8080, 30)
    println("Host: " .. cfg.host .. " Port: " .. cfg.port)

    // Assign to mutable field
    cfg.port = 9090
    println("New port: " .. cfg.port)

    return 0
}
