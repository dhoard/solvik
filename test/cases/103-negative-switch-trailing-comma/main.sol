module badtrailingcasecomma

class Main {
    public static run(args: String...): Long {
        switch 1 {
            case 1, 2,: {}
        }
        return 0
    }
}
