package delegnoniface

struct Thing { public func new(): Self { return Self {} } }

struct Wrapper {
    thing: Thing
    delegate Thing to thing
}

struct Main { public func run(args: String...): Long { return 0 } }
