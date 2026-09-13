package brace.newlines

// Entirely Allman-style: every opening brace sits on the line after its
// header. Must behave identically to the single-line-brace equivalent.

struct Greeter
{

    public func greet(who: String): String
    {
        return "hello " .. who
    }
}

struct Main
{

    public func run(args: String...): Long
    {
        System.getOut().println(Greeter.greet("world"))

        let mutable i: Long = 0
        while i < 3
        {
            i += 1
        }

        if i == 3
        {
            System.getOut().println("counted")
        }
        else
        {
            System.getOut().println("broken")
        }

        for x in [1, 2]
        {
            System.getOut().println(x)
        }

        switch i
        {
            case 3:
            {
                System.getOut().println("three")
            }
            default:
            {
                System.getOut().println("other")
            }
        }

        try
        {
            throw Exception.new("boom")
        }
        catch (e: Exception)
        {
            System.getOut().println("caught " .. e)
        }
        finally
        {
            System.getOut().println("done")
        }

        let label: String = match i
        {
            3 => "match-three"
            _ => "match-other"
        }
        System.getOut().println(label)

        return 0
    }
}
