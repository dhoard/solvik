package randomnames

struct Main {

    pub func run(args: String...): Integer {
        Random.seed(42)
        System.getOut().println(Random.nextLong(10))
        System.getOut().println(Random.nextDouble())
        return 0
    }
}
