package finalizers
class Main {

    public static value(): Long {
        try { return 7 } finally { stdout.println("return cleanup") }
    }
    public static overridden(): Long {
        try { return 1 } finally { return 2 }
    }
    public static fail(): Void {
        try { throw "first" } catch (e) { throw "second" } finally { stdout.println("catch cleanup") }
    }
    public static run(args: String...): Long {
        stdout.println(Main.value())
        stdout.println(Main.overridden())
        try { Main.fail() } catch (e) { stdout.println(e) }
        try {
            try { throw "outer" } finally {
                try { throw "inner" } catch (e) { stdout.println(e) }
                try { stdout.println("nested") } finally { stdout.println("nested cleanup") }
            }
        } catch (e) { stdout.println(e) }
        return 0
    }
}
