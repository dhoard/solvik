package typedexc

struct AppError {
    public func new(): Self {
        return Self {}
    }
    public func boom(): Void { throw Exception.new("app") }
}

struct Main {
    public func run(args: String...): Integer {
        try {
            AppError.boom()
        } catch (e: Exception) {
            System.getOut().println("caught")
        }
        // Multiple typed catch clauses dispatch on the thrown value in
        // source order; a struct clause is reachable after an Exception
        // clause because struct values do not conform to Exception.
        try {
            throw AppError.new()
        } catch (a: Exception) {
            System.getOut().println("first")
        } catch (b: AppError) {
            System.getOut().println("second")
        }
        // Finally always runs.
        let mutable ran: Boolean = false
        try {
            throw Exception.new("fin")
        } catch (c: Exception) {
            ran = true
        } finally {
            System.getOut().println("finally")
        }
        System.getOut().println(ran)
        return 0
    }
}
