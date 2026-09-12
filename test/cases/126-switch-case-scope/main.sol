package scope
class Main {
    public static run(args: String...): Long {
        let y: Long = 1
        switch 1 {
            case 1: { let y2: Long = 2 }
            case 2: { let w: Long = 3 }
        }
        System.out().println(y)
        return 0
    }
}
