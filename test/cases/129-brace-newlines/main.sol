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
        stdout.println(Greeter.greet("world"))

        let mutable i: Long = 0
        while i < 3
        {
            i += 1
        }

        if i == 3
        {
            stdout.println("counted")
        }
        else
        {
            stdout.println("broken")
        }

        for x in [1, 2]
        {
            stdout.println(x)
        }

        switch i
        {
            case 3:
            {
                stdout.println("three")
            }
            default:
            {
                stdout.println("other")
            }
        }

        try
        {
            throw "boom"
        }
        catch (e)
        {
            stdout.println("caught " .. e)
        }
        finally
        {
            stdout.println("done")
        }

        let label: String = match i
        {
            3 => "match-three"
            _ => "match-other"
        }
        stdout.println(label)

        return 0
    }
}
