package regression

class Main {
    pub static run(args: String...): Int {
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
