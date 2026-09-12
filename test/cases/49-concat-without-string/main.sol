package invalid
class Main {

    public static run(args: String...): Long {
        System.getOut().println(true .. false)
        return 0
    }
}
