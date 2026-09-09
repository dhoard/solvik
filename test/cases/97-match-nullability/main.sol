module matchnullability

class Main {

    public static run(args: String...): Long {
        maybe: String? = null
        value: String = match true {
            true => maybe
            false => "ok"
        }
        return 0
    }
}
