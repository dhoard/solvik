module delegnoniface

class Thing { public static new(): Self { return Self {} } }

class Wrapper {
    thing: Thing
    delegate Thing to thing
}

class Main { public static run(args: String...): Long { return 0 } }
