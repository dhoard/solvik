package matchnullability

struct Main {

    public func run(args: String...): Long {
        let maybe: String? = null
        let value: String = match true {
            true => maybe
            false => "ok"
        }
        return 0
    }
}
