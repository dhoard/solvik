module badliteral
class Main {
    public static run(args: String...): Long {
        stdout.println(9223372036854775808)
        return 0
    }
}
