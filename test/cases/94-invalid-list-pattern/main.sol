module invalidlistpattern

class Main {

    public static run(args: String...): Long {
        let value: Long = match 1 {
            [1] => 1
            _ => 0
        }
        return value
    }
}
