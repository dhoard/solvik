package conversions

class Main {

    public static run(args: String...): Long {
        System.out().println(Long.from("42"))
        System.out().println(Long.from(3.9))
        System.out().println(Double.from("2.5"))
        System.out().println(Double.from(7))
        System.out().println(String.from(123))
        System.out().println(String.from(true))
        System.out().println(Boolean.from("true"))
        System.out().println(Boolean.from("false"))
        System.out().println(Char.from(65))
        // introspection
        let x: Long = 5
        let s: String = "hi"
        let o: Object = x
        System.out().println(Type.of(x))
        System.out().println(Type.of(s))
        System.out().println(Type.isType(o, "Long"))
        System.out().println(Type.isType(s, "String"))
        System.out().println(Type.isType(s, "Long"))
        return 0
    }
}
