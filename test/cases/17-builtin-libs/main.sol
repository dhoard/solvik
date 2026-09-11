package builtinlibs

class Main {

    public static run(args: String...): Long {
        // regex
        let r: Regex = Regex.new("[0-9]+")
        System.out().println(r.matches("abc123"))
        System.out().println(r.matches("abc"))
        System.out().println(r.find("abc123def"))
        let all: List<String> = r.all("1 22 333")
        System.out().println(all.size())
        System.out().println(r.replace("a1b2", "#"))
        // base64
        System.out().println(Base64.encode("hello"))
        System.out().println(Base64.decode("aGVsbG8="))
        // hashing (deterministic digests)
        System.out().println(Hash.md5("abc"))
        System.out().println(Hash.sha1("abc"))
        System.out().println(Hash.sha256("abc"))
        // json round-trip through a map
        let m: Map<String, Object> = { "a": 1 }
        let j: String = Json.stringify(m)
        System.out().println(j)
        return 0
    }
}
