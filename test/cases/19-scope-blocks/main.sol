package test

class Main {

    public static run(args: String...): Long {
        // Basic scope block
        {
            let x: Long = 5
            System.getOut().println("x = " .. x)
        }

        // Nested scope block (shadowing is now a compile error)
        let outer: Long = 10
        {
            let inner: Long = 20
            System.getOut().println("shadowed: " .. inner)
        }
        System.getOut().println("restored: " .. outer)

        // Nested scope blocks
        {
            let a: Long = 1
            {
                let b: Long = 2
                System.getOut().println("a = " .. a .. ", b = " .. b)
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
        System.getOut().println("loop count: " .. count)

        return 0
    }
}
