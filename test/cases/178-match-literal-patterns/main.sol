package litpats

class Main {

    public static run(args: String...): Long {
        let f: Double = 1.5
        match f {
            1.5 => System.getOut().println("float hit")
            _ => System.getOut().println("float miss")
        }
        let b: Boolean = true
        match b {
            true => System.getOut().println("bool hit")
            false => System.getOut().println("bool miss")
        }
        let c: Char = 'q'
        match c {
            'q' => System.getOut().println("char hit")
            _ => System.getOut().println("char miss")
        }
        let s: String = "hit"
        match s {
            "hit" => System.getOut().println("string hit")
            _ => System.getOut().println("string miss")
        }
        // Non-matching literals fall through to the wildcard.
        let g: Double = 2.5
        match g {
            1.5 => System.getOut().println("float hit")
            _ => System.getOut().println("float miss")
        }
        return 0
    }
}
