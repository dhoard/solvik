package processstreams

class Main {
    pub static run(args: String...): Int {
        command_args: List<String> = []
        p: Process = Process::new("printf hi", command_args)
        p.start()
        p.wait()
        output: String = p.stdout().readAll()
        stdout.println(output)
        return 0
    }
}
