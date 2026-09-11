package stringiteration

class Main {

    public static run(args: String...): Long {
        let mutable count: Long = 0
        let mutable found: Bool = false
        for c in "hello" {
            count = count + 1
            if c == 'o' { found = true }
        }
        System.out().println(count)
        System.out().println(found)
        return 0
    }
}
