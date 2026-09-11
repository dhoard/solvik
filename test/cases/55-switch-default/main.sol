package sw

class Main {

    public static run(args: String...): Long {
        // Unmatched subject must run default, never a case body.
        let x: Long = 99
        switch x {
            case 1: {
                System.out().println("one")
            }
            case 2: {
                System.out().println("two")
            }
            default: {
                System.out().println("other")
            }
        }
        // Matching subject runs its own case.
        let y: Long = 1
        switch y {
            case 1: {
                System.out().println("one")
            }
            case 2: {
                System.out().println("two")
            }
            default: {
                System.out().println("other")
            }
        }
        // Default declared first.
        let z: Long = 7
        switch z {
            default: {
                System.out().println("other")
            }
            case 1: {
                System.out().println("one")
            }
        }
        // No default and no match: nothing runs.
        let w: Long = 3
        switch w {
            case 4: {
                System.out().println("four")
            }
            case 5: {
                System.out().println("five")
            }
        }
        System.out().println("end")
        return 0
    }
}
