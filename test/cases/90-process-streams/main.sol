package processstreams

struct Main {

    pub func run(args: String...): Integer {
        let commandArgs: List<String> = []
        let p: Process = Process.new("printf hi", commandArgs)
        p.start()
        p.wait()
        let output: String = p.stdout().readAll()
        System.getOut().println(output)
        return 0
    }
}
