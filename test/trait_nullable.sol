package trait_nullable

trait Drawable {
    func draw() -> String
}

struct Circle {
    pub mut radius: Float,

    pub func draw() -> String {
        return "Circle(r=" .. string(radius) .. ")"
    }
}

func main() -> Int {
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
