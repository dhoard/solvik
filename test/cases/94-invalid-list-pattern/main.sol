package invalidlistpattern

struct Main {

    pub func run(args: String...): Integer {
        let value: Long = match 1 {
            [1] => 1
            _ => 0
        }
        return value
    }
}
