module ifacenotimpl

interface Named { name(): String }
interface Sized { size(): Long }

class Thing implements Named { public name(): String { return "x" } }

class Wrapper implements Named {
    thing: Thing
    delegate Sized to thing
}

class Main { public static run(args: String...): Long { return 0 } }
