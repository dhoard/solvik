package badtrailingcasecomma

struct Main {
    public static func run(args: String...): Long {
        switch 1 {
            case 1, 2,: {}
        }
        return 0
    }
}
