package nr

class Main {
    pub static run(args: String...): Int {
        // Regex.find returns String? (null when there is no match).
        re: Regex = Regex::new("^a+$")
        m: String? = re.find("bbb")
        stdout.println(m ?? "nomatch")
        m2: String? = re.find("aaa")
        stdout.println(m2 ?? "nomatch")
        return 0
    }
}
