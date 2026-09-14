package org.solvik.transpiler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Focused tests for the automatic struct monitor and the {@code atomic(...)}
 * statement: the generated Java representation and the end-to-end runtime
 * behavior under real Java threads.
 *
 * <p>Structural assertions read the deterministic generated source; runtime
 * assertions transpile, {@code javac} with {@code -Xlint:all -Werror}, and run
 * the program in a bounded subprocess so a monitor regression fails the build
 * instead of hanging it.</p>
 */
final class MonitorTest {
    private static final Path BASEDIR = Path.of(System.getProperty("solvik.basedir", System.getProperty("user.dir")))
            .toAbsolutePath().normalize();
    private static final String JAVA = executable("java");
    private static final String JAVAC = executable("javac");

    private MonitorTest() {}

    @Test
    void everyStructInstanceCarriesAFairMonitorAndStableOrderId() throws Exception {
        String java = generate("""
                package monitor

                struct Counter {
                    var value: Long
                    pub func new(): Self { return Self { value: 0, } }
                    pub func increment(self) { self.value += 1 }
                    pub func get(self): Long { return self.value }
                }

                struct Main {
                    pub func run(args: String...): Integer { return 0 }
                }
                """);
        require(java.contains("private final ReentrantReadWriteLock __monitor = new ReentrantReadWriteLock(true);"),
                "struct is missing its fair reentrant read/write lock: " + java);
        require(java.contains("private final long __monitorId = RT.nextLockId();"),
                "struct is missing its stable lock-order id: " + java);
        require(java.contains("@Override public ReentrantReadWriteLock __monitorLock()"),
                "struct does not expose the internal lock accessor");
        require(java.contains("@Override public long __monitorOrder()"),
                "struct does not expose the internal lock-order accessor");
        require(java.contains("interface Lockable { ReentrantReadWriteLock __monitorLock(); long __monitorOrder(); }"),
                "internal lockable contract missing from runtime");
    }

    @Test
    void instanceMethodsLockAndUnlockInFinally() throws Exception {
        String java = generate("""
                package monitor

                struct Counter {
                    var value: Long
                    pub func new(): Self { return Self { value: 0, } }
                    pub func increment(self) { self.value += 1 }
                    pub func get(self): Long { return self.value }
                }

                struct Main {
                    pub func run(args: String...): Integer { return 0 }
                }
                """);
        require(java.contains("var __lock = this.__monitorLock().writeLock();"),
                "instance method does not acquire the receiver write lock: " + java);
        require(java.contains("__lock.lock();"), "instance method does not lock");
        require(java.contains("__lock.unlock();"), "instance method does not unlock");
        require(java.contains("} finally {"), "instance method lock is not released through a finally");
        // The write lock object is a cached field of the lock, so no guard object
        // is allocated per ordinary instance-method call.
        require(!java.contains("new RT.AtomicGuard(__lock)"),
                "instance method path must not allocate a monitor guard");
    }

    @Test
    void staticMethodsAreNotSerializedOnTheInstanceMonitor() throws Exception {
        String java = generate("""
                package monitor

                struct Main {
                    pub func run(args: String...): Integer {
                        return 0
                    }
                }
                """);
        require(!java.contains("var __lock"), "static-only struct should not wrap any method in a monitor: " + java);
        require(java.contains("public static int run(RT.SList<String> v_args)"),
                "entry point changed shape: " + java);
    }

    @Test
    void directConstructorOptimizationStillInitializesMonitorMetadata() throws Exception {
        String java = generate("""
                package monitor

                struct Point {
                    x: Long
                    y: Long
                    pub func new(x: Long, y: Long): Self { return Self { x: x, y: y, } }
                }

                struct Main {
                    pub func run(args: String...): Integer {
                        let p: Point = Point.new(1, 2)
                        return 0
                    }
                }
                """);
        require(java.contains("new __S_Point(1L, 2L)"), "trivial factory did not lower to the constructor: " + java);
        require(!java.contains("__S_Point.__new"), "trivial factory was not eliminated");
        require(java.contains("private final ReentrantReadWriteLock __monitor = new ReentrantReadWriteLock(true);"),
                "optimized constructor path lost the monitor field initializer");
        require(java.contains("private final long __monitorId = RT.nextLockId();"),
                "optimized constructor path lost the lock-order id initializer");
    }

    @Test
    void traitDefaultsAndDelegationCannotBypassTheReceiverMonitor() throws Exception {
        String java = generate("""
                package monitor

                trait Greeter {
                    func name(self): String
                    func greet(self): String { return "hi " .. self.name() }
                }

                struct Person implements Greeter {
                    n: String
                    pub func new(n: String): Self { return Self { n: n, } }
                    pub func name(self): String { return self.n }
                }

                struct Wrapper implements Greeter {
                    inner: Person
                    delegate Greeter to inner
                    pub func new(name: String): Self { return Self { inner: Person.new(name), } }
                }

                struct Main {
                    pub func run(args: String...): Integer { return 0 }
                }
                """);
        require(java.contains("public interface __I_Greeter extends RT.Lockable"),
                "trait interface does not participate in the lockable contract: " + java);
        int defaultStart = java.indexOf("public default String greet()");
        require(defaultStart >= 0, "trait default method missing");
        String defaultBody = java.substring(defaultStart, java.indexOf("public static final class __S_Person", defaultStart));
        require(defaultBody.contains("this.__monitorLock().writeLock()") && defaultBody.contains("__lock.unlock();"),
                "trait default body does not hold the receiver monitor: " + defaultBody);
        // Wrapper forwards greet() to its delegate field; that generated method
        // is an instance method and is therefore monitor-wrapped too.
        require(java.contains("this.f_inner.greet()"), "delegated forwarding call missing");
    }

    @Test
    void atomicLocksInDeterministicOrderAndReleasesInFinally() throws Exception {
        String java = generate("""
                package monitor

                struct Account {
                    var balanceValue: Integer
                    pub func new(balance: Integer): Self { return Self { balanceValue: balance, } }
                    pub func balance(self): Integer { return self.balanceValue }
                    pub func withdraw(self, amount: Integer) { self.balanceValue -= amount }
                    pub func deposit(self, amount: Integer) { self.balanceValue += amount }
                }

                struct Bank {
                    pub func transfer(source: Account, destination: Account, amount: Integer): Boolean {
                        atomic(destination, source) {
                            if source.balance() < amount {
                                return false
                            }
                            source.withdraw(amount)
                            destination.deposit(amount)
                            return true
                        }
                    }
                }

                struct Main {
                    pub func run(args: String...): Integer { return 0 }
                }
                """);
        // Targets are bound to locals in source order before the guard is taken.
        int destination = java.indexOf("RT.Lockable __atomic0 = (RT.Lockable)(v_destination);");
        int source = java.indexOf("RT.Lockable __atomic1 = (RT.Lockable)(v_source);");
        require(destination >= 0 && source > destination,
                "atomic targets were not evaluated once, left to right, before locking: " + java);
        require(java.contains("RT.AtomicGuard __guard"), "atomic guard missing");
        require(java.contains("} finally {"), "atomic block does not release through a finally");
        require(java.contains(".close();"), "atomic guard is not closed");
        // The runtime deduplicates by reference identity, sorts by the hidden
        // lock-order id, and releases in reverse acquisition order.
        require(java.contains("new java.util.IdentityHashMap<>()"),
                "atomic targets must be deduplicated by identity");
        require(java.contains("unique[j].__monitorOrder() > order"),
                "atomic targets must be ordered by their hidden lock-order id");
        require(java.contains("for (int i = locks.length - 1; i >= 0; i--) locks[i].unlock();"),
                "atomic locks must be released in reverse order");
    }

    @Test
    void concurrentIncrementsAndOppositeLockOrdersComplete(@TempDir Path work) throws Exception {
        String java = run("""
                package monitorrun

                struct Counter {
                    var value: Long
                    pub func new(): Self { return Self { value: 0, } }
                    pub func increment(self) { self.value += 1 }
                    pub func get(self): Long { return self.value }
                }

                struct Worker implements Runnable {
                    counter: Counter
                    start: Semaphore
                    rounds: Long
                    pub func new(counter: Counter, start: Semaphore, rounds: Long): Self {
                        return Self { counter: counter, start: start, rounds: rounds, }
                    }
                    pub func run(self) {
                        self.start.acquire()
                        var i: Long = 0
                        while i < self.rounds {
                            atomic(self.counter) {
                                self.counter.increment()
                            }
                            i += 1
                        }
                    }
                }

                struct Mover implements Runnable {
                    first: Counter
                    second: Counter
                    start: Semaphore
                    rounds: Long
                    pub func new(first: Counter, second: Counter, start: Semaphore, rounds: Long): Self {
                        return Self { first: first, second: second, start: start, rounds: rounds, }
                    }
                    pub func run(self) {
                        self.start.acquire()
                        var i: Long = 0
                        while i < self.rounds {
                            atomic(self.first, self.second) {
                                self.first.increment()
                                self.second.increment()
                            }
                            i += 1
                        }
                    }
                }

                struct Main {
                    pub func run(args: String...): Integer {
                        let counter: Counter = Counter.new()
                        let gate: Semaphore = Semaphore.new(0)
                        let workers: List<Thread> = List<Thread>.new()
                        var i: Long = 0
                        while i < 6 {
                            let worker: Thread = Thread.new(Worker.new(counter, gate, 400))
                            worker.start()
                            workers.add(worker)
                            i += 1
                        }
                        var w: Long = 0
                        while w < 6 {
                            gate.release()
                            w += 1
                        }
                        for worker in workers {
                            worker.join()
                        }
                        System.getOut().println(counter.get())

                        let a: Counter = Counter.new()
                        let b: Counter = Counter.new()
                        let start: Semaphore = Semaphore.new(0)
                        let ab: Thread = Thread.new(Mover.new(a, b, start, 1000))
                        let ba: Thread = Thread.new(Mover.new(b, a, start, 1000))
                        ab.start()
                        ba.start()
                        start.release()
                        start.release()
                        ab.join()
                        ba.join()
                        System.getOut().println(a.get())
                        System.getOut().println(b.get())
                        return 0
                    }
                }
                """, work, "MonitorConcurrent");
        List<String> lines = java.lines().toList();
        require(lines.equals(List.of("2400", "2000", "2000")),
                "concurrent monitor behavior is wrong: " + lines);
    }

    private static String generate(String source) throws Exception {
        return Transpiler.toJava("monitor.sol", source, "Generated");
    }

    /** Transpiles, compiles, and runs a Solvik program, returning its stdout. */
    private static String run(String source, Path work, String className) throws Exception {
        String java = Transpiler.toJava("monitor.sol", source, className);
        Path javaFile = work.resolve(className + ".java");
        Files.writeString(javaFile, java, StandardCharsets.UTF_8);
        Path classes = Files.createDirectories(work.resolve("classes"));
        ProcessResult compilation = run(work, List.of(JAVAC, "--release", "17", "-Xlint:all", "-Werror",
                "-d", classes.toString(), javaFile.toString()));
        require(compilation.exitCode() == 0, "javac failed:\n" + compilation.output());
        ProcessResult execution = run(work, List.of(JAVA, "-cp", classes.toString(), className));
        require(execution.exitCode() == 0, "monitor program failed:\n" + execution.output());
        return execution.output();
    }

    private static ProcessResult run(Path directory, List<String> command) throws IOException, InterruptedException {
        Path output = Files.createTempFile(directory, "monitor-", ".out");
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(directory.toFile());
        builder.redirectErrorStream(true);
        builder.redirectOutput(output.toFile());
        try {
            Process process = builder.start();
            if (!process.waitFor(60, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor();
                throw new AssertionError("process timed out: " + command);
            }
            return new ProcessResult(process.exitValue(), Files.readString(output, StandardCharsets.UTF_8));
        } finally {
            Files.deleteIfExists(output);
        }
    }

    private static String executable(String name) {
        String fileName = System.getProperty("os.name").toLowerCase().contains("win") ? name + ".exe" : name;
        return Path.of(System.getProperty("java.home"), "bin", fileName).toString();
    }

    private record ProcessResult(int exitCode, String output) {}

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
