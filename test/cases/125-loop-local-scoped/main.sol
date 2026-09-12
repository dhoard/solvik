package scope
class Main {
    public static run(args: String...): Long {
        while false {
            let inner: Long = 5
        }
        System.getOut().println(inner)
        return 0
    }
}
