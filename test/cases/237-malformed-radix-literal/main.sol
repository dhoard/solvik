package malformedradix

struct Main {

    pub func run(args: String...): Integer {
        // '2' is not a binary digit; the lexer must not read it as decimal.
        System.getOut().println(0b102)
        return 0
    }
}
