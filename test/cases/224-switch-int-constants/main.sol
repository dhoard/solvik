package switchintconstants

struct Main {

    public func run(args: String...): Integer {
        // Constant Integer cases: first matching case wins, default last.
        let x: Integer = 2
        switch x {
            case 1: {
                System.getOut().println("one")
            }
            case 2, 3: {
                System.getOut().println("two-or-three")
            }
            default: {
                System.getOut().println("other")
            }
        }

        // Duplicate case values keep first-wins ordering.
        let y: Integer = 99
        switch y {
            case 1: {
                return 10
            }
            case 1: {
                return 20
            }
            case 99: {
                System.getOut().println("ninety-nine")
            }
        }

        // Long subjects keep their exact values.
        let z: Long = 5
        switch z {
            case 5: {
                System.getOut().println("long-five")
            }
            case 10000000000: {
                return 40
            }
        }

        // break/continue inside a switch still bind to the enclosing loop.
        let mutable i: Long = 0
        while i < 10 {
            switch i {
                case 2: {
                    i += 1
                    continue
                }
                case 5: {
                    break
                }
                default: {
                    i += 1
                }
            }
        }
        System.getOut().println(i)
        return 0
    }
}
