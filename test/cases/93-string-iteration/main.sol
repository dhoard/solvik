package stringiteration

class Main {

    public static run(args: String...): Long {
        let mutable count: Long = 0
        let mutable found: Bool = false
        for c in "hello" {
            count = count + 1
            if c == 'o' { found = true }
        }
        stdout.println(count)
        stdout.println(found)
        return 0
    }
}
