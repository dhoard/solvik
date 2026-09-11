package conversions

class Main {

    public static run(args: String...): Long {
        stdout.println(Long.from("42"))
        stdout.println(Long.from(3.9))
        stdout.println(Double.from("2.5"))
        stdout.println(Double.from(7))
        stdout.println(String.from(123))
        stdout.println(String.from(true))
        stdout.println(Bool.from(5))
        stdout.println(Bool.from(0))
        stdout.println(Char.from(65))
        // introspection
        let x: Long = 5
        let s: String = "hi"
        let o: Object = x
        stdout.println(Type.of(x))
        stdout.println(Type.of(s))
        stdout.println(Type.isType(o, "Long"))
        stdout.println(Type.isType(s, "String"))
        stdout.println(Type.isType(s, "Long"))
        return 0
    }
}
