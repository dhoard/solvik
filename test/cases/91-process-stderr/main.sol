package processstderr

struct Main {

    public func run(args: String...): Integer {
        let commandArgs: List<String> = ["-c", "printf err >&2"]
        let p: Process = Process.new("sh", commandArgs)
        p.start()
        let output: String = p.stderr().readAll()
        p.wait()
        System.getOut().println(output)
        return 0
    }
}
