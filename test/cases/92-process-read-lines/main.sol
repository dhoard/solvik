package processreadlines

class Main {
    pub static run(args: String...): Int {
        command_args: List<String> = ["-c", "printf 'a\\nb\\n'"]
        p: Process = Process::new("sh", command_args)
        p.start()
        first: String? = p.stdout().readln()
        second: String? = p.stdout().readln()
        p.wait()
        stdout.println(first ?? "missing")
        stdout.println(second ?? "missing")
        return 0
    }
}
