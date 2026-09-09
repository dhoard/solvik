package processlargeoutput

class Main {
    pub static run(args: String...): Int {
        command_args: List<String> = ["-c", "yes x | head -c 200000"]
        p: Process = Process::new("sh", command_args)
        p.start()
        p.wait()
        output: String = p.stdout().readAll()
        if output.length() == 200000 {
            return 0
        }
        return 1
    }
}
