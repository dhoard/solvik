module patlists

// Regression: list patterns could not be parsed (the closing ']' was never
// consumed), and nested element patterns (binds, literals) were treated as
// always-matching.

class Main {

    public static run(args: String...): Long {
        let l: List<Long> = List<Long>.new()
        l.add(1)
        l.add(2)
        match l {
            [1, x] => stdout.println("first one, second " .. x)
            _ => stdout.println("no")
        }
        match l {
            [2, 1] => stdout.println("reversed")
            [1, 2] => stdout.println("exact")
            _ => stdout.println("other")
        }
        match l {
            [] => stdout.println("empty")
            [1, 2, 3] => stdout.println("three")
            [1, 2] => stdout.println("two")
            _ => stdout.println("other")
        }
        return 0
    }
}
