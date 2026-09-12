package invalid
class Main {

    public static nothing(): Void {}
    public static run(args: String...): Long {
        System.getOut().println("void=" .. Main.nothing())
        return 0
    }
}
