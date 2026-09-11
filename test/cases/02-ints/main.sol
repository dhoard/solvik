package ints

class Main {

    public static run(args: String...): Long {
        let a: Long = 7
        let b: Long = 3
        System.out().println(a + b)
        System.out().println(a - b)
        System.out().println(a * b)
        System.out().println(a / b)
        System.out().println(a % b)
        System.out().println(-a)
        System.out().println(10 > 3)
        System.out().println(3 >= 10)
        System.out().println(2 == 2)
        System.out().println(2 != 5)
        System.out().println(true && false)
        System.out().println(true || false)
        System.out().println(!true)
        let mutable x: Long = 5
        x += 2
        x -= 1
        x *= 3
        System.out().println(x)
        return 0
    }
}
