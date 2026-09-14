package stalerregion
struct Main {

    pub func boom() {
        throw Exception.new("real")
    }
    pub func run(args: String...): Integer {
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
