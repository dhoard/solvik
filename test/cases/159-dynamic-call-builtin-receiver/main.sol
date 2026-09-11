package dynbuiltin

class Main {

    public static run(args: String...): Long {
        let s: String = "hello"
        let o: Object = s
        o.length()
        return 0
    }
}
