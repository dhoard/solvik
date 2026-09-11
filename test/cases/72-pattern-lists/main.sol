package patlists

// Regression: list patterns could not be parsed (the closing ']' was never
// consumed), and nested element patterns (binds, literals) were treated as
// always-matching.

class Main {

    public static run(args: String...): Long {
        let l: List<Long> = List<Long>.new()
        l.add(1)
        l.add(2)
        match l {
            [1, x] => System.out().println("first one, second " .. x)
            _ => System.out().println("no")
        }
        match l {
            [2, 1] => System.out().println("reversed")
            [1, 2] => System.out().println("exact")
            _ => System.out().println("other")
        }
        match l {
            [] => System.out().println("empty")
            [1, 2, 3] => System.out().println("three")
            [1, 2] => System.out().println("two")
            _ => System.out().println("other")
        }
        return 0
    }
}
