package invalidlistpattern

class Main {
    pub static run(args: String...): Int {
        value: Int = match 1 {
            [1] => 1
            _ => 0
        }
        return value
    }
}
