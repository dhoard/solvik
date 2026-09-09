package interfacecycle

interface A extends B {}
interface B extends A {}

class Main {
    pub static run(args: String...): Int {
        return 0
    }
}
