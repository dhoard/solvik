package nr

class Main {

    public static run(args: String...): Long {
        // Regex.find returns String? (null when there is no match).
        let re: Regex = Regex.new("^a+$")
        let m: String? = re.find("bbb")
        System.out().println(m ?? "nomatch")
        let m2: String? = re.find("aaa")
        System.out().println(m2 ?? "nomatch")
        return 0
    }
}
