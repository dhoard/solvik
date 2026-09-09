module inheritancecycle

class A extends B {}
class B extends A {}

class Main {

    public static run(args: String...): Long {
        return 0
    }
}
