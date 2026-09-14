package usesuper

struct Base {
    pub func new(): Self { return Self {} }
    pub func value(self): Long { return 1 }
}

struct Sub {
    pub func go(self): Long { return super.value() }
}

struct Main { pub func run(args: String...): Integer { return 0 } }
