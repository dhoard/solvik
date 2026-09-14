package interfacecycle

trait A extends B {}
trait B extends A {}

struct Main {

    pub func run(args: String...): Integer {
        return 0
    }
}
