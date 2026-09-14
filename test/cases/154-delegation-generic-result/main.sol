package deleggenresult

trait Source<T> {
    func get(self): T
}

struct LongSource implements Source<Long> {
    pub func new(): Self {
        return Self {}
    }
    pub func get(self): Long {
        return 41
    }
}

struct W implements Source<Long> {
    s: LongSource
    delegate Source<Long> to s
    pub func new(): Self {
        return Self { s: LongSource.new(), }
    }
}

struct Main {
    pub func run(args: String...): Integer {
        // The delegated method must carry the substituted result type, not
        // an erased type variable.
        let w: W = W.new()
        let x: Long = w.get() + 1
        System.getOut().println(x)
        let src: Source<Long> = w
        let y: Long = src.get() + 1
        System.getOut().println(y)
        return 0
    }
}
