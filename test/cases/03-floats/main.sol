package floats

struct Main {

    public func run(args: String...): Integer {
        let a: Double = 2.5
        let b: Double = 1.5
        System.getOut().println(a + b)
        System.getOut().println(a - b)
        System.getOut().println(a * b)
        System.getOut().println(a / b)
        System.getOut().println(Math.sqrt(4.0))
        System.getOut().println(Math.floor(2.7))
        System.getOut().println(Math.ceil(2.1))
        System.getOut().println(Math.round(2.5))
        System.getOut().println(Math.pow(2.0, 10.0))
        System.getOut().println(3.0 > 2.0)
        return 0
    }
}
