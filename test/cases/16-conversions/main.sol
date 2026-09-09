package conversions

class Main {
    pub static run(args: String...): Int {
        stdout.println(Int::from("42"))
        stdout.println(Int::from(3.9))
        stdout.println(Float::from("2.5"))
        stdout.println(Float::from(7))
        stdout.println(String::from(123))
        stdout.println(String::from(true))
        stdout.println(Bool::from(5))
        stdout.println(Bool::from(0))
        stdout.println(Char::from(65))
        // introspection
        x: Int = 5
        s: String = "hi"
        o: Object = x
        stdout.println(Type::of(x))
        stdout.println(Type::of(s))
        stdout.println(Type::isType(o, "Int"))
        stdout.println(Type::isType(s, "String"))
        stdout.println(Type::isType(s, "Int"))
        return 0
    }
}
