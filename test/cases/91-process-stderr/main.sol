module processstderr

class Main {

    public static run(args: String...): Long {
        commandArgs: List<String> = ["-c", "printf err >&2"]
        p: Process = Process.new("sh", commandArgs)
        p.start()
        output: String = p.stderr().readAll()
        p.wait()
        stdout.println(output)
        return 0
    }
}
