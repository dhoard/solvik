package ints

class Main {

    public static run(args: String...): Long {
        let a: Long = 7
        let b: Long = 3
        stdout.println(a + b)
        stdout.println(a - b)
        stdout.println(a * b)
        stdout.println(a / b)
        stdout.println(a % b)
        stdout.println(-a)
        stdout.println(10 > 3)
        stdout.println(3 >= 10)
        stdout.println(2 == 2)
        stdout.println(2 != 5)
        stdout.println(true && false)
        stdout.println(true || false)
        stdout.println(!true)
        let mutable x: Long = 5
        x += 2
        x -= 1
        x *= 3
        stdout.println(x)
        return 0
    }
}
