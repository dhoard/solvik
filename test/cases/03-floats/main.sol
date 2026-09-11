package floats

class Main {

    public static run(args: String...): Long {
        let a: Double = 2.5
        let b: Double = 1.5
        System.out().println(a + b)
        System.out().println(a - b)
        System.out().println(a * b)
        System.out().println(a / b)
        System.out().println(Math.sqrt(4.0))
        System.out().println(Math.floor(2.7))
        System.out().println(Math.ceil(2.1))
        System.out().println(Math.round(2.5))
        System.out().println(Math.pow(2.0, 10.0))
        System.out().println(3.0 > 2.0)
        return 0
    }
}
