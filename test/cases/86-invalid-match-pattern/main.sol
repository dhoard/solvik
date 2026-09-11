package invalidmatchpattern

class Main {

    public static run(args: String...): Long {
        let value: Long = match 1 {
            "wrong" => 1
            _ => 2
        }
        return value
    }
}
