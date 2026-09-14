package processlargeoutput

struct Main {

    public func run(args: String...): Integer {
        let commandArgs: List<String> = ["-c", "yes x | head -c 200000"]
        let p: Process = Process.new("sh", commandArgs)
        p.start()
        p.wait()
        let output: String = p.stdout().readAll()
        if output.length() == 200000 {
            return 0
        }
        return 1
    }
}
