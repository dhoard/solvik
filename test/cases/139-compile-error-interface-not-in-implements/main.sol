package ifacenotimpl

trait Named { func name(self): String }
trait Sized { func size(self): Long }

struct Thing implements Named { pub func name(self): String { return "x" } }

struct Wrapper implements Named {
    thing: Thing
    delegate Sized to thing
}

struct Main { pub func run(args: String...): Integer { return 0 } }
