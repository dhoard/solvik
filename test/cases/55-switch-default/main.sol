package sw

class Main {
    pub static run(args: String...): Int {
        // Unmatched subject must run default, never a case body.
        x: Int = 99
        switch x {
            case 1: {
                stdout.println("one")
            }
            case 2: {
                stdout.println("two")
            }
            default: {
                stdout.println("other")
            }
        }
        // Matching subject runs its own case.
        y: Int = 1
        switch y {
            case 1: {
                stdout.println("one")
            }
            case 2: {
                stdout.println("two")
            }
            default: {
                stdout.println("other")
            }
        }
        // Default declared first.
        z: Int = 7
        switch z {
            default: {
                stdout.println("other")
            }
            case 1: {
                stdout.println("one")
            }
        }
        // No default and no match: nothing runs.
        w: Int = 3
        switch w {
            case 4: {
                stdout.println("four")
            }
            case 5: {
                stdout.println("five")
            }
        }
        stdout.println("end")
        return 0
    }
}
