module stressstr

class Main {

    public static run(args: String...): Long {
        mutable s: String = ""
        mutable i: Long = 0
        while i < 50000 {
            s = s .. "abcdefgh"
            i += 1
        }
        stdout.println(s.length())
        stdout.println(s.startsWith("abc"))
        stdout.println(s.endsWith("h"))
        return 0
    }
}
