module processstreams

class Main {

    public static run(args: String...): Long {
        commandArgs: List<String> = []
        p: Process = Process.new("printf hi", commandArgs)
        p.start()
        p.wait()
        output: String = p.stdout().readAll()
        stdout.println(output)
        return 0
    }
}
