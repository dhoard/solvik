package processstderr

class Main {

    public static run(args: String...): Long {
        let commandArgs: List<String> = ["-c", "printf err >&2"]
        let p: Process = Process.new("sh", commandArgs)
        p.start()
        let output: String = p.stderr().readAll()
        p.wait()
        stdout.println(output)
        return 0
    }
}
