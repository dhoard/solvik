package ints

struct Main {

    public static func run(args: String...): Long {
        let a: Long = 7
        let b: Long = 3
        System.getOut().println(a + b)
        System.getOut().println(a - b)
        System.getOut().println(a * b)
        System.getOut().println(a / b)
        System.getOut().println(a % b)
        System.getOut().println(-a)
        System.getOut().println(10 > 3)
        System.getOut().println(3 >= 10)
        System.getOut().println(2 == 2)
        System.getOut().println(2 != 5)
        System.getOut().println(true && false)
        System.getOut().println(true || false)
        System.getOut().println(!true)
        let mutable x: Long = 5
        x += 2
        x -= 1
        x *= 3
        System.getOut().println(x)
        return 0
    }
}
