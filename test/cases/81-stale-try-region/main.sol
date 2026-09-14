package stalerregion
struct Main {

    public func boom() {
        throw Exception.new("real")
    }
    public func run(args: String...): Integer {
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
