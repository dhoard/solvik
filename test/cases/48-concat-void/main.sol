package invalid
struct Main {

    public static func nothing(): Void {}
    public static func run(args: String...): Long {
        System.getOut().println("void=" .. Main.nothing())
        return 0
    }
}
