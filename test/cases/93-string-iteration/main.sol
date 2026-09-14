package stringiteration

struct Main {

    pub func run(args: String...): Integer {
        var count: Long = 0
        var found: Boolean = false
        for c in "hello" {
            count = count + 1
            if c == 'o' { found = true }
        }
        System.getOut().println(count)
        System.getOut().println(found)
        return 0
    }
}
