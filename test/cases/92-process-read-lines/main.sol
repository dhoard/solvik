module processreadlines

class Main {

    public static run(args: String...): Long {
        commandArgs: List<String> = ["-c", "printf 'a\\nb\\n'"]
        p: Process = Process.new("sh", commandArgs)
        p.start()
        first: String? = p.stdout().readln()
        second: String? = p.stdout().readln()
        p.wait()
        stdout.println(first ?? "missing")
        stdout.println(second ?? "missing")
        return 0
    }
}
