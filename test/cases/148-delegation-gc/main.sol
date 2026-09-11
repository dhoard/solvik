package deleggc

interface Named { name(): String }

class Person implements Named {
    nameValue: String
    public static new(n: String): Self { return Self { nameValue: n, } }
    public name(): String { return self.nameValue }
}

class Wrapper implements Named {
    person: Person
    delegate Named to person
    public static new(n: String): Self { return Self { person: Person.new(n), } }
}

class Main {
    public static run(args: String...): Long {
        // Stress allocation so the collector runs; the private delegate field
        // is an ordinary reference and must keep its target alive.
        let mutable i: Long = 0
        let mutable last: String = ""
        while i < 50000 {
            let w: Wrapper = Wrapper.new("p" .. i)
            last = w.name()
            i += 1
        }
        System.out().println(last)
        return 0
    }
}
