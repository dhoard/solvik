package badliteral
class Main {
    pub static run(args: String...): Int {
        stdout.println(9223372036854775808)
        return 0
    }
}
