package interfacecycle

interface A extends B {}
interface B extends A {}

struct Main {

    public func run(args: String...): Long {
        return 0
    }
}
