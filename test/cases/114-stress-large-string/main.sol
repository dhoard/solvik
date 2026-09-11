package stressstr

class Main {

    public static run(args: String...): Long {
        let mutable s: String = ""
        let mutable i: Long = 0
        while i < 50000 {
            s = s .. "abcdefgh"
            i += 1
        }
        System.out().println(s.length())
        System.out().println(s.startsWith("abc"))
        System.out().println(s.endsWith("h"))
        return 0
    }
}
