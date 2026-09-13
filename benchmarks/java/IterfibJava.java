public final class IterfibJava {
    static final long MOD = 1_000_000_007L;

    public static void main(String[] args) {
        long a = 0;
        long b = 1;
        for (long i = 0; i < 9_000_000L; i++) {
            long t = (a + b) % MOD;
            a = b;
            b = t;
        }
        System.out.println(b);
    }
}
