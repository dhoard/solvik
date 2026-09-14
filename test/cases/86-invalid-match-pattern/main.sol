package invalidmatchpattern

struct Main {

    pub func run(args: String...): Integer {
        let value: Long = match 1 {
            "wrong" => 1
            _ => 2
        }
        return value
    }
}
