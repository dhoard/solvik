package typedexc

class AppError {
    public static new(): Self {
        return Self {}
    }
    public static boom(): Void { throw Exception.new("app") }
}

class Main {
    public static run(args: String...): Long {
        try {
            AppError.boom()
        } catch (e: Exception) {
            System.out().println("caught")
        }
        // Multiple typed catch clauses dispatch on the thrown value in
        // source order; a class clause is reachable after an Exception
        // clause because class values do not conform to Exception.
        try {
            throw AppError.new()
        } catch (a: Exception) {
            System.out().println("first")
        } catch (b: AppError) {
            System.out().println("second")
        }
        // Finally always runs.
        let mutable ran: Boolean = false
        try {
            throw Exception.new("fin")
        } catch (c: Exception) {
            ran = true
        } finally {
            System.out().println("finally")
        }
        System.out().println(ran)
        return 0
    }
}
