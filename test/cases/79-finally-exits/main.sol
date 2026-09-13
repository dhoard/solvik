package finalizers
struct Main {

    public static func value(): Long {
        try { return 7 } finally { System.getOut().println("return cleanup") }
    }
    public static func overridden(): Long {
        try { return 1 } finally { return 2 }
    }
    public static func fail(): Void {
        try { throw Exception.new("first") } catch (e: Exception) { throw Exception.new("second") } finally { System.getOut().println("catch cleanup") }
    }
    public static func run(args: String...): Long {
        System.getOut().println(Main.value())
        System.getOut().println(Main.overridden())
        try { Main.fail() } catch (e: Exception) { System.getOut().println(e) }
        try {
            try { throw Exception.new("outer") } finally {
                try { throw Exception.new("inner") } catch (e: Exception) { System.getOut().println(e) }
                try { System.getOut().println("nested") } finally { System.getOut().println("nested cleanup") }
            }
        } catch (e: Exception) { System.getOut().println(e) }
        return 0
    }
}
