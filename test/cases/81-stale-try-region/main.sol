package stalerregion
class Main {

    public static boom(): Void {
        throw Exception.new("real")
    }
    public static run(args: String...): Long {
        let x: Boolean = true
        while (x) {
            try {
                break
            } catch (e: Exception) { System.out().println("BAD catch") }
        }
        Main.boom()
        return 0
    }
}
