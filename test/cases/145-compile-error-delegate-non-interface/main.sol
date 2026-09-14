package delegnoniface

struct Thing { pub func new(): Self { return Self {} } }

struct Wrapper {
    thing: Thing
    delegate Thing to thing
}

struct Main { pub func run(args: String...): Integer { return 0 } }
