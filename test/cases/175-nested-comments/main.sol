package nestedcomments

/* outer comment
   /* inner comment
        /* innermost */
   still inner */
still outer */
// line comment
class Main {

    public static run(args: String...): Long {
        /* single-line block comment */
        System.out().println("ok") // trailing line comment
        return 0
    }
}
