package radixpatterns
class Main {

    public static run(args: String...): Long {
        match 42 {
            0x2a => System.getOut().println("hex")
            _ => System.getOut().println("wrong")
        }
        match 42 {
            0o52 => System.getOut().println("octal")
            _ => System.getOut().println("wrong")
        }
        match 42 {
            0b10_1010 => System.getOut().println("binary")
            _ => System.getOut().println("wrong")
        }
        System.getOut().println(-9223372036854775808)
        return 0
    }
}
