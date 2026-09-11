package randomnames

class Main {

    public static run(args: String...): Long {
        Random.seed(42)
        stdout.println(Random.nextLong(10))
        stdout.println(Random.nextDouble())
        return 0
    }
}
