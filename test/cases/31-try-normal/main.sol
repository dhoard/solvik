package regression

class Main {

    public static run(args: String...): Long {
        try {
            System.out().println("ok")
        } catch (e) {
            System.out().println("wrong")
        }
        try {
            System.out().println("work")
        } catch (e) {
            System.out().println("wrong")
        } finally {
            System.out().println("cleaned")
        }
        return 0
    }
}
