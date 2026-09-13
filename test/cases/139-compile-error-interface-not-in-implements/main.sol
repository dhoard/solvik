package ifacenotimpl

interface Named { func name(self): String }
interface Sized { func size(self): Long }

struct Thing implements Named { public func name(self): String { return "x" } }

struct Wrapper implements Named {
    thing: Thing
    delegate Sized to thing
}

struct Main { public static func run(args: String...): Long { return 0 } }
