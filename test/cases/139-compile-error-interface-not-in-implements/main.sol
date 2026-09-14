package ifacenotimpl

trait Named { func name(self): String }
trait Sized { func size(self): Long }

struct Thing implements Named { public func name(self): String { return "x" } }

struct Wrapper implements Named {
    thing: Thing
    delegate Sized to thing
}

struct Main { public func run(args: String...): Long { return 0 } }
