package regression

class Main {
    pub static run(args: String...): Int {
        stdout.println(Math::abs(-9223372036854775807 - 1))
        return 0
    }
}
