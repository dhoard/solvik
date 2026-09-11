package randomnames

class Main {

    public static run(args: String...): Long {
        Random.seed(42)
        System.out().println(Random.nextLong(10))
        System.out().println(Random.nextDouble())
        return 0
    }
}
