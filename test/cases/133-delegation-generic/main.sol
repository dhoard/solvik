package deleggeneric

interface Source<T> { func get(self): T }

struct StringSource implements Source<String> {
    public func new(): Self { return Self {} }
    public func get(self): String { return "x" }
}

struct Wrapper implements Source<String> {
    source: StringSource
    delegate Source<String> to source
    public func new(): Self { return Self { source: StringSource.new(), } }
}

struct Main {
    public func run(args: String...): Long {
        let w: Wrapper = Wrapper.new()
        System.getOut().println(w.get())
        let s: Source<String> = w
        System.getOut().println(s.get())
        return 0
    }
}
