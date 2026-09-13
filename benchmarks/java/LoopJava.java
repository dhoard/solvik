public final class LoopJava {
    public static void main(String[] args) {
        long sum = 0;
        long i = 0;
        while (i < 50_000_000L) {
            sum += i * 3 - 1;
            i++;
        }
        System.out.println(sum);
    }
}
