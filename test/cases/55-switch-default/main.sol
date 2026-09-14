package sw

struct Main {

    public func run(args: String...): Integer {
        // Unmatched subject must run default, never a case body.
        let x: Long = 99
        switch x {
            case 1: {
                System.getOut().println("one")
            }
            case 2: {
                System.getOut().println("two")
            }
            default: {
                System.getOut().println("other")
            }
        }
        // Matching subject runs its own case.
        let y: Long = 1
        switch y {
            case 1: {
                System.getOut().println("one")
            }
            case 2: {
                System.getOut().println("two")
            }
            default: {
                System.getOut().println("other")
            }
        }
        // Default declared first.
        let z: Long = 7
        switch z {
            default: {
                System.getOut().println("other")
            }
            case 1: {
                System.getOut().println("one")
            }
        }
        // No default and no match: nothing runs.
        let w: Long = 3
        switch w {
            case 4: {
                System.getOut().println("four")
            }
            case 5: {
                System.getOut().println("five")
            }
        }
        System.getOut().println("end")
        return 0
    }
}
