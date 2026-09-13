import java.util.regex.Pattern;

public final class RegexJava {
    private static final Pattern PATTERN = Pattern.compile("[0-9]+");

    public static void main(String[] args) {
        long count = 0;
        for (long i = 0; i < 200_000L; i++) {
            if (PATTERN.matcher("abc123def").find()) {
                count++;
            }
        }
        System.out.println(count);
    }
}
