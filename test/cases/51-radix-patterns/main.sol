package radixpatterns
class Main {
    pub static run(args: String...): Int {
        match 42 {
            0x2a => stdout.println("hex")
            _ => stdout.println("wrong")
        }
        match 42 {
            0o52 => stdout.println("octal")
            _ => stdout.println("wrong")
        }
        match 42 {
            0b10_1010 => stdout.println("binary")
            _ => stdout.println("wrong")
        }
        stdout.println(-9223372036854775808)
        return 0
    }
}
