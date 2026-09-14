package nr

struct Main {

    pub func run(args: String...): Integer {
        // Regex.find returns String? (null when there is no match).
        let re: Regex = Regex.new("^a+$")
        let m: String? = re.find("bbb")
        System.getOut().println(m ?? "nomatch")
        let m2: String? = re.find("aaa")
        System.getOut().println(m2 ?? "nomatch")
        return 0
    }
}
