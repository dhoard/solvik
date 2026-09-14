package builtinlibs

struct Main {

    pub func run(args: String...): Integer {
        // regex
        let r: Regex = Regex.new("[0-9]+")
        System.getOut().println(r.matches("abc123"))
        System.getOut().println(r.matches("abc"))
        System.getOut().println(r.find("abc123def"))
        let all: List<String> = r.all("1 22 333")
        System.getOut().println(all.size())
        System.getOut().println(r.replace("a1b2", "#"))
        // base64
        System.getOut().println(Base64.encode("hello"))
        System.getOut().println(Base64.decode("aGVsbG8="))
        // hashing (deterministic digests)
        System.getOut().println(Hash.md5("abc"))
        System.getOut().println(Hash.sha1("abc"))
        System.getOut().println(Hash.sha256("abc"))
        // json round-trip through a map
        let m: Map<String, Object> = { "a": 1 }
        let j: String = Json.stringify(m)
        System.getOut().println(j)
        return 0
    }
}
