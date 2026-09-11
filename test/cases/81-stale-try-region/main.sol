package stalerregion
class Main {

    public static boom(): Void {
        throw "real"
    }
    public static run(args: String...): Long {
        let x: Bool = true
        while (x) {
            try {
                break
            } catch (e) { System.out().println("BAD catch") }
        }
        Main.boom()
        return 0
    }
}
