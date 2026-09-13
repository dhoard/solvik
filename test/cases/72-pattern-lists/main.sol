package patlists

// Regression: list patterns could not be parsed (the closing ']' was never
// consumed), and nested element patterns (binds, literals) were treated as
// always-matching.

struct Main {

    public static func run(args: String...): Long {
        let l: List<Long> = List<Long>.new()
        l.add(1)
        l.add(2)
        match l {
            [1, x] => System.getOut().println("first one, second " .. x)
            _ => System.getOut().println("no")
        }
        match l {
            [2, 1] => System.getOut().println("reversed")
            [1, 2] => System.getOut().println("exact")
            _ => System.getOut().println("other")
        }
        match l {
            [] => System.getOut().println("empty")
            [1, 2, 3] => System.getOut().println("three")
            [1, 2] => System.getOut().println("two")
            _ => System.getOut().println("other")
        }
        return 0
    }
}
