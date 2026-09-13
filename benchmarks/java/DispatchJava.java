public final class DispatchJava {
    interface Worker { long work(long x); }
    static final class Fast implements Worker { public long work(long x) { return x * 3 - 1; } }
    static final class Slow implements Worker { public long work(long x) { return x * 5 + 2; } }

    public static void main(String[] args) {
        Worker fast = new Fast();
        Worker slow = new Slow();
        long total = 0;
        for (long i = 0; i < 50_000_000L; i++) {
            if ((i % 2L) == 0L) {
                total += fast.work(i);
            } else {
                total += slow.work(i);
            }
        }
        System.out.println(total);
    }
}
