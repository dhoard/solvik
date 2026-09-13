package delegnoniface

struct Thing { public static func new(): Self { return Self {} } }

struct Wrapper {
    thing: Thing
    delegate Thing to thing
}

struct Main { public static func run(args: String...): Long { return 0 } }
