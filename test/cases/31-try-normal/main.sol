package regression

class Main {

    public static run(args: String...): Long {
        try {
            stdout.println("ok")
        } catch (e) {
            stdout.println("wrong")
        }
        try {
            stdout.println("work")
        } catch (e) {
            stdout.println("wrong")
        } finally {
            stdout.println("cleaned")
        }
        return 0
    }
}
