package regression

struct Main {

    public func run(args: String...): Integer {
        try {
            System.getOut().println("ok")
        } catch (e: Exception) {
            System.getOut().println("wrong")
        }
        try {
            System.getOut().println("work")
        } catch (e: Exception) {
            System.getOut().println("wrong")
        } finally {
            System.getOut().println("cleaned")
        }
        return 0
    }
}
