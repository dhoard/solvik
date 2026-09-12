package finalizers
class Main {

    public static value(): Long {
        try { return 7 } finally { System.out().println("return cleanup") }
    }
    public static overridden(): Long {
        try { return 1 } finally { return 2 }
    }
    public static fail(): Void {
        try { throw Exception.new("first") } catch (e: Exception) { throw Exception.new("second") } finally { System.out().println("catch cleanup") }
    }
    public static run(args: String...): Long {
        System.out().println(Main.value())
        System.out().println(Main.overridden())
        try { Main.fail() } catch (e: Exception) { System.out().println(e) }
        try {
            try { throw Exception.new("outer") } finally {
                try { throw Exception.new("inner") } catch (e: Exception) { System.out().println(e) }
                try { System.out().println("nested") } finally { System.out().println("nested cleanup") }
            }
        } catch (e: Exception) { System.out().println(e) }
        return 0
    }
}
