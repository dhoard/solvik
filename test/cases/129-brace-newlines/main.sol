package brace.newlines

// Entirely Allman-style: every opening brace sits on the line after its
// header. Must behave identically to the single-line-brace equivalent.

class Greeter
{

    public static greet(who: String): String
    {
        return "hello " .. who
    }
}

class Main
{

    public static run(args: String...): Long
    {
        System.out().println(Greeter.greet("world"))

        let mutable i: Long = 0
        while i < 3
        {
            i += 1
        }

        if i == 3
        {
            System.out().println("counted")
        }
        else
        {
            System.out().println("broken")
        }

        for x in [1, 2]
        {
            System.out().println(x)
        }

        switch i
        {
            case 3:
            {
                System.out().println("three")
            }
            default:
            {
                System.out().println("other")
            }
        }

        try
        {
            throw Exception.new("boom")
        }
        catch (e: Exception)
        {
            System.out().println("caught " .. e)
        }
        finally
        {
            System.out().println("done")
        }

        let label: String = match i
        {
            3 => "match-three"
            _ => "match-other"
        }
        System.out().println(label)

        return 0
    }
}
