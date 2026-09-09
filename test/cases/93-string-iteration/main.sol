package stringiteration

class Main {
    pub static run(args: String...): Int {
        mut count: Int = 0
        mut found: Bool = false
        for c in "hello" {
            count = count + 1
            if c == 'o' { found = true }
        }
        stdout.println(count)
        stdout.println(found)
        return 0
    }
}
