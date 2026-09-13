package nullableadelegate

interface Named { func name(self): String }

struct Wrapper implements Named {
    person: Named?
    delegate Named to person
}

struct Main { public func run(args: String...): Long { return 0 } }
