package static_methods

// Type-associated functions (static methods): User.new(...), Point.zero(),
// Box<Int>.new(...). `new` is a convention, not a required constructor.

struct User {
    pub name: String
    secret: String

    pub static func new(name: String) -> User {
        return User { name: name, secret: "s-" .. name }
    }

    static func fromJson(text: String) -> User? {
        if text == "" {
            return null
        }
        return User { name: text, secret: "j-" .. text }
    }

    pub func greet() -> String {
        return "hi " .. self.name
    }
}

struct Point {
    pub x: Int
    pub y: Int

    pub static func zero() -> Point {
        return Point { x: 0, y: 0 }
    }

    pub static func axis(which: String) -> Point {
        if which == "x" {
            return Point { x: 1, y: 0 }
        }
        return Point { x: 0, y: 1 }
    }
}

struct Duration {
    pub millis: Int

    pub static func seconds(n: Int) -> Duration {
        return Duration { millis: n * 1000 }
    }
}

struct Box<T> {
    pub value: T

    pub static func new(value: T) -> Box<T> {
        return Box { value: value }
    }
}

func ensurePositive(n: Int) -> Int {
    if n <= 0 {
        throw "non-positive"
    }
    return n
}

func main() -> Int {
    // The conventional constructor name.
    user: User = User.new("Doug")
    test.assertEq(user.greet(), "hi Doug")
    // Same-package code may read private members produced by a static.
    test.assertEq(user.secret, "s-Doug")

    // Factories are ordinary associated functions; any name works.
    origin: Point = Point.zero()
    test.assertEq(origin.x + origin.y, 0)
    onX: Point = Point.axis("x")
    test.assertEq(onX.x, 1)
    d: Duration = Duration.seconds(5)
    test.assertEq(d.millis, 5000)

    // Nullable factory result.
    missing: User? = User.fromJson("")
    test.assertNull(missing)
    present: User? = User.fromJson("raw")
    test.assertEq(present.secret, "j-raw")

    // Statics may call other statics of the same type.
    doubled: Duration = Duration.seconds(Duration.seconds(1).millis / 500)
    test.assertEq(doubled.millis, 2000)

    // Explicit type arguments instantiate the owning struct's parameters.
    b: Box<Int> = Box<Int>.new(7)
    test.assertEq(b.value, 7)
    // Without explicit arguments the parameter is inferred from the payload.
    b2: Box<String> = Box.new("text")
    test.assertEq(b2.value, "text")

    // A static body is checked like any function body.
    mut caught: String = ""
    try {
        ensurePositive(-1)
    } catch (e: Exception) {
        caught = e.message
    }
    test.assertEq(caught, "non-positive")

    println("static methods passed")
    return 0
}
