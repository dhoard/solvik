package conversions

struct Main {

    public func run(args: String...): Integer {
        System.getOut().println(Long.from("42"))
        System.getOut().println(Long.from(3.9))
        System.getOut().println(Double.from("2.5"))
        System.getOut().println(Double.from(7))
        System.getOut().println(String.from(123))
        System.getOut().println(String.from(true))
        System.getOut().println(Boolean.from("true"))
        System.getOut().println(Boolean.from("false"))
        System.getOut().println(Char.from(65))
        // introspection
        let x: Long = 5
        let s: String = "hi"
        let o: Object = x
        System.getOut().println(Type.of(x))
        System.getOut().println(Type.of(s))
        System.getOut().println(Type.isType(o, "Long"))
        System.getOut().println(Type.isType(s, "String"))
        System.getOut().println(Type.isType(s, "Long"))
        return 0
    }
}
