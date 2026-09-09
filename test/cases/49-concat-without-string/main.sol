module invalid
class Main {

    public static run(args: String...): Long {
        stdout.println(true .. false)
        return 0
    }
}
