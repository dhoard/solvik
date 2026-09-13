import java.util.ArrayList;
import java.util.List;

public final class CollectionsJava {
    public static void main(String[] args) {
        List<Long> list = new ArrayList<>();
        for (long i = 0; i < 2_000_000L; i++) {
            list.add(i);
        }
        long sum = 0;
        for (long v : list) {
            sum += v;
        }
        System.out.println(sum);
    }
}
