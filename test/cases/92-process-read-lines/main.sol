package processreadlines

struct Main {

    pub func run(args: String...): Integer {
        let commandArgs: List<String> = ["-c", "printf 'a\\nb\\n'"]
        let p: Process = Process.new("sh", commandArgs)
        p.start()
        let first: String? = p.stdout().readln()
        let second: String? = p.stdout().readln()
        p.wait()
        System.getOut().println(first ?? "missing")
        System.getOut().println(second ?? "missing")
        return 0
    }
}
