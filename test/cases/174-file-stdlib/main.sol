package filestdlib

class Main {

    public static run(args: String...): Long {
        // Runs from the repository root (see test/run.sh).
        let path: String = "tmp-file-stdlib-note.txt"

        File.write(path, "hello file")
        System.out().println(File.exists(path))
        System.out().println(File.read(path))

        let entries: List<String> = File.listDir("test/cases/174-file-stdlib")
        System.out().println(entries.contains("main.sol"))

        File.delete(path)
        System.out().println(File.exists(path))
        return 0
    }
}
