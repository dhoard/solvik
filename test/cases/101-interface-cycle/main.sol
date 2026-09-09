module interfacecycle

interface A extends B {}
interface B extends A {}

class Main {

    public static run(args: String...): Long {
        return 0
    }
}
