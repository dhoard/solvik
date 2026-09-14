package delegnotimpl

trait Named { func name(self): String }

struct Wrapper implements Named {
    count: Long
    delegate Named to count
}

struct Main { public func run(args: String...): Integer { return 0 } }
