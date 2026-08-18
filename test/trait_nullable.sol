package trait_nullable

trait Drawable {
    func draw() -> string
}

struct Circle {
    pub mut radius: float,

    pub func draw() -> string {
        return "Circle(r=" .. string(radius) .. ")"
    }
}

func main() -> int {
    mut maybe: Drawable? = null
    if maybe == null {
        println("null as expected")
    }

    maybe = Circle { radius: 5.0 }
    if maybe != null {
        println("not null: " .. typeOf(maybe))
    }

    return 0
}
