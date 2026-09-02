// expected E067: runtime constraint failure through a generic method
package runtime_errors

struct Point {
    pub x: int
    pub y: int
}

struct Reporter {
    pub func show<T: Stringable>(v: T) -> string {
        return v.string()
    }
}

func main() -> int {
    r: Reporter = Reporter {  }
    println(r.show(Point { x: 1, y: 2 }))
    return 0
}
