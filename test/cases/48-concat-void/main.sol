module invalid
class Main {

    public static nothing(): Void {}
    public static run(args: String...): Long {
        stdout.println("void=" .. Main.nothing())
        return 0
    }
}
