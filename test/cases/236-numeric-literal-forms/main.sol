package numericforms

struct Main {

    pub func run(args: String...): Integer {
        // Decimal integers.
        System.getOut().println(0)
        System.getOut().println(42)
        System.getOut().println(1_000_000)
        // Hexadecimal, both cases of the prefix and digits.
        System.getOut().println(0x0)
        System.getOut().println(0xff)
        System.getOut().println(0XFF)
        System.getOut().println(0xCAFE_BABE)
        // Octal.
        System.getOut().println(0o0)
        System.getOut().println(0o755)
        System.getOut().println(0O755)
        System.getOut().println(0o7_5_5)
        // Binary.
        System.getOut().println(0b0)
        System.getOut().println(0b1010)
        System.getOut().println(0B1010)
        System.getOut().println(0b1010_0101)
        // Decimal floating point with separators and exponents.
        System.getOut().println(1.5)
        System.getOut().println(1_000.25)
        System.getOut().println(1e3)
        System.getOut().println(1.25e-10)
        // Float, Double, and BigDecimal suffixes.
        System.getOut().println(1.5f)
        System.getOut().println(1.5d)
        System.getOut().println(1.5bd)
        return 0
    }
}
