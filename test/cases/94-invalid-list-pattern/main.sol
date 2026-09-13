package invalidlistpattern

struct Main {

    public func run(args: String...): Long {
        let value: Long = match 1 {
            [1] => 1
            _ => 0
        }
        return value
    }
}
