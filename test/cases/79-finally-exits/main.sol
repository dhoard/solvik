package finalizers
struct Main {

    pub func value(): Long {
        try { return 7 } finally { System.getOut().println("return cleanup") }
    }
    pub func overridden(): Long {
        try { return 1 } finally { return 2 }
    }
    pub func fail() {
        try { throw Exception.new("first") } catch (e: Exception) { throw Exception.new("second") } finally { System.getOut().println("catch cleanup") }
    }
    pub func run(args: String...): Integer {
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
