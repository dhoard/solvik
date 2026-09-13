package processstreams

struct Main {

    public static func run(args: String...): Long {
        let commandArgs: List<String> = []
        let p: Process = Process.new("printf hi", commandArgs)
        p.start()
        p.wait()
        let output: String = p.stdout().readAll()
        System.getOut().println(output)
        return 0
    }
}
