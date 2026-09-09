package stalerregion
class Main {
    pub static boom(): Void {
        throw "real"
    }
    pub static run(args: String...): Int {
        x: Bool = true
        while (x) {
            try {
                break
            } catch (e) { stdout.println("BAD catch") }
        }
        Main::boom()
        return 0
    }
}
