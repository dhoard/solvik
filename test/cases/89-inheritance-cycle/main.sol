package inheritancecycle

class A extends B {}
class B extends A {}

class Main {
    pub static run(args: String...): Int {
        return 0
    }
}
