package deleggeneric

interface Source<T> { get(): T }

class StringSource implements Source<String> {
    public static new(): Self { return Self {} }
    public get(): String { return "x" }
}

class Wrapper implements Source<String> {
    source: StringSource
    delegate Source<String> to source
    public static new(): Self { return Self { source: StringSource.new(), } }
}

class Main {
    public static run(args: String...): Long {
        let w: Wrapper = Wrapper.new()
        stdout.println(w.get())
        let s: Source<String> = w
        stdout.println(s.get())
        return 0
    }
}
