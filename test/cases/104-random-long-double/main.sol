package randomnames

struct Main {

    public func run(args: String...): Long {
        Random.seed(42)
        System.getOut().println(Random.nextLong(10))
        System.getOut().println(Random.nextDouble())
        return 0
    }
}
