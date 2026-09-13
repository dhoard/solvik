package deleggc

interface Named { func name(self): String }

struct Person implements Named {
    nameValue: String
    public func new(n: String): Self { return Self { nameValue: n, } }
    public func name(self): String { return self.nameValue }
}

struct Wrapper implements Named {
    person: Person
    delegate Named to person
    public func new(n: String): Self { return Self { person: Person.new(n), } }
}

struct Main {
    public func run(args: String...): Long {
        // Stress allocation so the collector runs; the private delegate field
        // is an ordinary reference and must keep its target alive.
        let mutable i: Long = 0
        let mutable last: String = ""
        while i < 50000 {
            let w: Wrapper = Wrapper.new("p" .. i)
            last = w.name()
            i += 1
        }
        System.getOut().println(last)
        return 0
    }
}
