package sysmethods

struct Main {

    public func run(args: String...): Integer {
        // All three stream accessors still compile and work.
        System.getOut().println("streams ok")
        System.getErr().println("err ok")
        // stdin is /dev/null in the runner: readln at EOF is null.
        System.getOut().println(System.getIn().readln() == null)

        // The documented LF line delimiter.
        System.getOut().println(System.getLineSeparator() == "\n")

        // Zero-argument getEnv(): a non-null mutable snapshot. Mutating it
        // never touches the host environment or named lookup.
        let env: Map<String, String> = System.getEnv()
        System.getOut().println(env != null)
        env.put("SOLVIK_TEST_MUST_NOT_EXIST", "injected")
        System.getOut().println(System.getEnv("SOLVIK_TEST_MUST_NOT_EXIST") == null)

        // Wall clock: positive on normal Unix-epoch hosts.
        System.getOut().println(System.getCurrentTimeMillis() > 0)

        // Monotonic clock: two samples, compared as a difference only.
        let t0: Long = System.getNanoTime()
        let t1: Long = System.getNanoTime()
        System.getOut().println(t1 - t0 >= 0)

        // Property lifecycle with previous-value returns.
        let p0: String? = System.setProperty("k", "v1")
        System.getOut().println(p0 == null)
        let absent: String? = System.getProperty("missing")
        System.getOut().println(absent == null)
        System.getOut().println(System.getProperty("missing", "fb") == "fb")
        let p1: String? = System.setProperty("k", "v2")
        System.getOut().println(p1 == "v1")
        System.getOut().println(System.getProperty("k") == "v2")
        System.getOut().println(System.getProperty("k", "fb") == "v2")
        let cleared: String? = System.clearProperty("k")
        System.getOut().println(cleared == "v2")
        System.getOut().println(System.getProperty("k") == null)

        // Setting an empty value is distinct from clearing.
        System.setProperty("e", "")
        System.getOut().println(System.getProperty("e") == "")
        System.getOut().println(System.getProperty("e") != null)
        return 0
    }
}
