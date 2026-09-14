package badtrailingcasecomma

struct Main {
    pub func run(args: String...): Integer {
        switch 1 {
            case 1, 2,: {}
        }
        return 0
    }
}
