package invalid
class Main {

    public static nothing(): Void {}
    public static run(args: String...): Long {
        System.out().println("void=" .. Main.nothing())
        return 0
    }
}
