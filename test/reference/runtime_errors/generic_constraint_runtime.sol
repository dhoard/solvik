// expected E067: runtime constraint failure through a generic method
package runtime_errors

struct Point {
    pub x: Int
    pub y: Int
}

struct Reporter {
    pub func show<T: Stringable>(v: T) -> String {
        return v.string()
    }
}

func main() -> Int {
    r: Reporter = Reporter {  }
    println(r.show(Point { x: 1, y: 2 }))
    return 0
}
