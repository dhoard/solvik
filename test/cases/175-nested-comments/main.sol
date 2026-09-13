package nestedcomments

/* outer comment
   /* inner comment
        /* innermost */
   still inner */
still outer */
// line comment
struct Main {

    public func run(args: String...): Long {
        /* single-line block comment */
        System.getOut().println("ok") // trailing line comment
        return 0
    }
}
