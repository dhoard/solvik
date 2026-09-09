package matchnullability

class Main {
    pub static run(args: String...): Int {
        maybe: String? = null
        value: String = match true {
            true => maybe
            false => "ok"
        }
        return 0
    }
}
