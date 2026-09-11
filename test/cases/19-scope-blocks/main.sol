package test

class Main {

    public static run(args: String...): Long {
        // Basic scope block
        {
            let x: Long = 5
            System.out().println("x = " .. x)
        }

        // Shadowing
        let outer: Long = 10
        {
            let outer: Long = 20
            System.out().println("shadowed: " .. outer)
        }
        System.out().println("restored: " .. outer)

        // Nested scope blocks
        {
            let a: Long = 1
            {
                let b: Long = 2
                System.out().println("a = " .. a .. ", b = " .. b)
            }
            // b not visible here
        }

        // Empty scope block
        { }

        // break/continue through scope block
        let mutable count: Long = 0
        while (true) {
            {
                count = count + 1
                if (count > 3) {
                    break
                }
            }
        }
        System.out().println("loop count: " .. count)

        return 0
    }
}
