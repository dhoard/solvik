package nullableadelegate

trait Named { func name(self): String }

struct Wrapper implements Named {
    person: Named?
    delegate Named to person
}

struct Main { pub func run(args: String...): Integer { return 0 } }
