package deleggeneric

trait Source<T> { func get(self): T }

struct StringSource implements Source<String> {
    pub func new(): Self { return Self {} }
    pub func get(self): String { return "x" }
}

struct Wrapper implements Source<String> {
    source: StringSource
    delegate Source<String> to source
    pub func new(): Self { return Self { source: StringSource.new(), } }
}

struct Main {
    pub func run(args: String...): Integer {
        let w: Wrapper = Wrapper.new()
        System.getOut().println(w.get())
        let s: Source<String> = w
        System.getOut().println(s.get())
        return 0
    }
}
