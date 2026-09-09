package finalizers
class Main {
    pub static value(): Int {
        try { return 7 } finally { stdout.println("return cleanup") }
    }
    pub static overridden(): Int {
        try { return 1 } finally { return 2 }
    }
    pub static fail(): Void {
        try { throw "first" } catch (e) { throw "second" } finally { stdout.println("catch cleanup") }
    }
    pub static run(args: String...): Int {
        stdout.println(Main::value())
        stdout.println(Main::overridden())
        try { Main::fail() } catch (e) { stdout.println(e) }
        try {
            try { throw "outer" } finally {
                try { throw "inner" } catch (e) { stdout.println(e) }
                try { stdout.println("nested") } finally { stdout.println("nested cleanup") }
            }
        } catch (e) { stdout.println(e) }
        return 0
    }
}
