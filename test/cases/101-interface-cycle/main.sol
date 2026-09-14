package interfacecycle

trait A extends B {}
trait B extends A {}

struct Main {

    public func run(args: String...): Long {
        return 0
    }
}
