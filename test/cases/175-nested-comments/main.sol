package nestedcomments

/* outer comment
   /* inner comment
        /* innermost */
   still inner */
still outer */
// line comment
struct Main {

    pub func run(args: String...): Integer {
        /* single-line block comment */
        System.getOut().println("ok") // trailing line comment
        return 0
    }
}
