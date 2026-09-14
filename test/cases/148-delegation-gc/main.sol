package deleggc

trait Named { func name(self): String }

struct Person implements Named {
    nameValue: String
    pub func new(n: String): Self { return Self { nameValue: n, } }
    pub func name(self): String { return self.nameValue }
}

struct Wrapper implements Named {
    person: Person
    delegate Named to person
    pub func new(n: String): Self { return Self { person: Person.new(n), } }
}

struct Main {
    pub func run(args: String...): Integer {
        // Stress allocation so the collector runs; the private delegate field
        // is an ordinary reference and must keep its target alive.
        var i: Long = 0
        var last: String = ""
        while i < 50000 {
            let w: Wrapper = Wrapper.new("p" .. i)
            last = w.name()
            i += 1
        }
        System.getOut().println(last)
        return 0
    }
}
