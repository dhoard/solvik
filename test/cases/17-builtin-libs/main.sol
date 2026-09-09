package builtinlibs

class Main {
    pub static run(args: String...): Int {
        // regex
        r: Regex = Regex::new("[0-9]+")
        stdout.println(r.matches("abc123"))
        stdout.println(r.matches("abc"))
        stdout.println(r.find("abc123def"))
        all: List<String> = r.all("1 22 333")
        stdout.println(all.size())
        stdout.println(r.replace("a1b2", "#"))
        // base64
        stdout.println(Base64::encode("hello"))
        stdout.println(Base64::decode("aGVsbG8="))
        // hashing (deterministic digests)
        stdout.println(Hash::md5("abc"))
        stdout.println(Hash::sha1("abc"))
        stdout.println(Hash::sha256("abc"))
        // json round-trip through a map
        m: Map<String, Object> = { "a": 1 }
        j: String = Json::stringify(m)
        stdout.println(j)
        return 0
    }
}
