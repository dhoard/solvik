package processreadlines

class Main {

    public static run(args: String...): Long {
        let commandArgs: List<String> = ["-c", "printf 'a\\nb\\n'"]
        let p: Process = Process.new("sh", commandArgs)
        p.start()
        let first: String? = p.stdout().readln()
        let second: String? = p.stdout().readln()
        p.wait()
        System.out().println(first ?? "missing")
        System.out().println(second ?? "missing")
        return 0
    }
}
