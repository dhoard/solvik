package finnested

class Main {

    // Inner finally runs first, then outer; return value preserved.
    public static nested(): Long {
        try {
            try { return 10 } finally { System.getOut().println("inner") }
        } finally { System.getOut().println("outer") }
    }

    // A return inside a finally overrides the pending return value.
    public static finoverride(): Long {
        try {
            try { return 1 } finally { return 2 }
        } finally { System.getOut().println("outer ran") }
    }

    // A throw inside a finally is caught by the enclosing catch.
    public static finthrow(): Long {
        try {
            try { return 1 } finally { throw Exception.new("from-fin") }
        } catch (e: Exception) {
            System.getOut().println("caught: " .. e)
            return 7
        }
    }

    // Break leaves the loop but stays inside the try body: the finally
    // must not run at the break, only when the body completes.
    public static breakinside(): Long {
        let mutable x: Boolean = true
        try {
            while (x) {
                x = false
                break
            }
            System.getOut().println("body end")
        } finally { System.getOut().println("fin") }
        return 0
    }

    // Bare return in a void function still runs the finally.
    public static voidret(): Void {
        try {
            return
        } finally { System.getOut().println("vfin") }
    }

    public static run(args: String...): Long {
        System.getOut().println(Main.nested())
        System.getOut().println(Main.finoverride())
        System.getOut().println(Main.finthrow())
        System.getOut().println(Main.breakinside())
        Main.voidret()
        return 0
    }
}
