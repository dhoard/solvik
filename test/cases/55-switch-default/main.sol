module sw

class Main {

    public static run(args: String...): Long {
        // Unmatched subject must run default, never a case body.
        let x: Long = 99
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
        let y: Long = 1
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
        let z: Long = 7
        switch z {
            default: {
                stdout.println("other")
            }
            case 1: {
                stdout.println("one")
            }
        }
        // No default and no match: nothing runs.
        let w: Long = 3
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
