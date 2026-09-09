package processstderr

class Main {
    pub static run(args: String...): Int {
        command_args: List<String> = ["-c", "printf err >&2"]
        p: Process = Process::new("sh", command_args)
        p.start()
        output: String = p.stderr().readAll()
        p.wait()
        stdout.println(output)
        return 0
    }
}
