module finnested

class Main {

    // Inner finally runs first, then outer; return value preserved.
    public static nested(): Long {
        try {
            try { return 10 } finally { stdout.println("inner") }
        } finally { stdout.println("outer") }
    }

    // A return inside a finally overrides the pending return value.
    public static finoverride(): Long {
        try {
            try { return 1 } finally { return 2 }
        } finally { stdout.println("outer ran") }
    }

    // A throw inside a finally is caught by the enclosing catch.
    public static finthrow(): Long {
        try {
            try { return 1 } finally { throw "from-fin" }
        } catch (e) {
            stdout.println("caught: " .. e)
            return 7
        }
    }

    // Break leaves the loop but stays inside the try body: the finally
    // must not run at the break, only when the body completes.
    public static breakinside(): Long {
        mutable x: Bool = true
        try {
            while (x) {
                x = false
                break
            }
            stdout.println("body end")
        } finally { stdout.println("fin") }
        return 0
    }

    // Bare return in a void function still runs the finally.
    public static voidret(): Void {
        try {
            return
        } finally { stdout.println("vfin") }
    }

    public static run(args: String...): Long {
        stdout.println(Main.nested())
        stdout.println(Main.finoverride())
        stdout.println(Main.finthrow())
        stdout.println(Main.breakinside())
        Main.voidret()
        return 0
    }
}
