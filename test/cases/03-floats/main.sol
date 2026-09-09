package floats

class Main {
    pub static run(args: String...): Int {
        a: Float = 2.5
        b: Float = 1.5
        stdout.println(a + b)
        stdout.println(a - b)
        stdout.println(a * b)
        stdout.println(a / b)
        stdout.println(Math::sqrt(4.0))
        stdout.println(Math::floor(2.7))
        stdout.println(Math::ceil(2.1))
        stdout.println(Math::round(2.5))
        stdout.println(Math::pow(2.0, 10.0))
        stdout.println(3.0 > 2.0)
        return 0
    }
}
