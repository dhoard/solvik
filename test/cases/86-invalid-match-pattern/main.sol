package invalidmatchpattern

class Main {
    pub static run(args: String...): Int {
        value: Int = match 1 {
            "wrong" => 1
            _ => 2
        }
        return value
    }
}
