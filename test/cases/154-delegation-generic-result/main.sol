package deleggenresult

interface Source<T> {
    get(): T
}

class LongSource implements Source<Long> {
    public static new(): Self {
        return Self {}
    }
    public get(): Long {
        return 41
    }
}

class W implements Source<Long> {
    s: LongSource
    delegate Source<Long> to s
    public static new(): Self {
        return Self { s: LongSource.new(), }
    }
}

class Main {
    public static run(args: String...): Long {
        // The delegated method must carry the substituted result type, not
        // an erased type variable.
        let w: W = W.new()
        let x: Long = w.get() + 1
        System.out().println(x)
        let src: Source<Long> = w
        let y: Long = src.get() + 1
        System.out().println(y)
        return 0
    }
}
