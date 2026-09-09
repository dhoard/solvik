package ints

class Main {
    pub static run(args: String...): Int {
        a: Int = 7
        b: Int = 3
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
        mut x: Int = 5
        x += 2
        x -= 1
        x *= 3
        stdout.println(x)
        return 0
    }
}
