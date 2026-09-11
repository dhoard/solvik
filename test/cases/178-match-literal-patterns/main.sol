package litpats

class Main {

    public static run(args: String...): Long {
        let f: Double = 1.5
        match f {
            1.5 => System.out().println("float hit")
            _ => System.out().println("float miss")
        }
        let b: Bool = true
        match b {
            true => System.out().println("bool hit")
            false => System.out().println("bool miss")
        }
        let c: Char = 'q'
        match c {
            'q' => System.out().println("char hit")
            _ => System.out().println("char miss")
        }
        let s: String = "hit"
        match s {
            "hit" => System.out().println("string hit")
            _ => System.out().println("string miss")
        }
        // Non-matching literals fall through to the wildcard.
        let g: Double = 2.5
        match g {
            1.5 => System.out().println("float hit")
            _ => System.out().println("float miss")
        }
        return 0
    }
}
