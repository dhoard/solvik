package finalizers
class Main {

    public static value(): Long {
        try { return 7 } finally { System.out().println("return cleanup") }
    }
    public static overridden(): Long {
        try { return 1 } finally { return 2 }
    }
    public static fail(): Void {
        try { throw "first" } catch (e) { throw "second" } finally { System.out().println("catch cleanup") }
    }
    public static run(args: String...): Long {
        System.out().println(Main.value())
        System.out().println(Main.overridden())
        try { Main.fail() } catch (e) { System.out().println(e) }
        try {
            try { throw "outer" } finally {
                try { throw "inner" } catch (e) { System.out().println(e) }
                try { System.out().println("nested") } finally { System.out().println("nested cleanup") }
            }
        } catch (e) { System.out().println(e) }
        return 0
    }
}
