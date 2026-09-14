package matchnullability

struct Main {

    pub func run(args: String...): Integer {
        let maybe: String? = null
        let value: String = match true {
            true => maybe
            false => "ok"
        }
        return 0
    }
}
