package invalidmatchpattern

struct Main {

    public static func run(args: String...): Long {
        let value: Long = match 1 {
            "wrong" => 1
            _ => 2
        }
        return value
    }
}
