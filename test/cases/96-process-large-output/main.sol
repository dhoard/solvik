module processlargeoutput

class Main {

    public static run(args: String...): Long {
        commandArgs: List<String> = ["-c", "yes x | head -c 200000"]
        p: Process = Process.new("sh", commandArgs)
        p.start()
        p.wait()
        output: String = p.stdout().readAll()
        if output.length() == 200000 {
            return 0
        }
        return 1
    }
}
