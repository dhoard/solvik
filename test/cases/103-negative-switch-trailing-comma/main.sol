package badtrailingcasecomma

struct Main {
    public func run(args: String...): Long {
        switch 1 {
            case 1, 2,: {}
        }
        return 0
    }
}
