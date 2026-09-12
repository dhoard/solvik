package stringiteration

class Main {

    public static run(args: String...): Long {
        let mutable count: Long = 0
        let mutable found: Boolean = false
        for c in "hello" {
            count = count + 1
            if c == 'o' { found = true }
        }
        System.getOut().println(count)
        System.getOut().println(found)
        return 0
    }
}
