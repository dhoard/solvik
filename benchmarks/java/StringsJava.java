public final class StringsJava {
    public static void main(String[] args) {
        String s = "";
        for (long i = 0; i < 100_000L; i++) {
            s = s + i;
        }
        System.out.println(s.length());
    }
}
