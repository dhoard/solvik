package badtrailingcasecomma

struct Main {
    public func run(args: String...): Integer {
        switch 1 {
            case 1, 2,: {}
        }
        return 0
    }
}
