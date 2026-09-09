package finnested

class Main {
    // Inner finally runs first, then outer; return value preserved.
    pub static nested(): Int {
        try {
            try { return 10 } finally { stdout.println("inner") }
        } finally { stdout.println("outer") }
    }

    // A return inside a finally overrides the pending return value.
    pub static finoverride(): Int {
        try {
            try { return 1 } finally { return 2 }
        } finally { stdout.println("outer ran") }
    }

    // A throw inside a finally is caught by the enclosing catch.
    pub static finthrow(): Int {
        try {
            try { return 1 } finally { throw "from-fin" }
        } catch (e) {
            stdout.println("caught: " .. e)
            return 7
        }
    }

    // Break leaves the loop but stays inside the try body: the finally
    // must not run at the break, only when the body completes.
    pub static breakinside(): Int {
        mut x: Bool = true
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
    pub static voidret(): Void {
        try {
            return
        } finally { stdout.println("vfin") }
    }

    pub static run(args: String...): Int {
        stdout.println(Main::nested())
        stdout.println(Main::finoverride())
        stdout.println(Main::finthrow())
        stdout.println(Main::breakinside())
        Main::voidret()
        return 0
    }
}
