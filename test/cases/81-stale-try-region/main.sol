package stalerregion
struct Main {

    public static func boom(): Void {
        throw Exception.new("real")
    }
    public static func run(args: String...): Long {
        let x: Boolean = true
        while (x) {
            try {
                break
            } catch (e: Exception) { System.getOut().println("BAD catch") }
        }
        Main.boom()
        return 0
    }
}
