package radixpatterns
class Main {

    public static run(args: String...): Long {
        match 42 {
            0x2a => System.out().println("hex")
            _ => System.out().println("wrong")
        }
        match 42 {
            0o52 => System.out().println("octal")
            _ => System.out().println("wrong")
        }
        match 42 {
            0b10_1010 => System.out().println("binary")
            _ => System.out().println("wrong")
        }
        System.out().println(-9223372036854775808)
        return 0
    }
}
