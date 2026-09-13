import java.util.ArrayList;
import java.util.List;

public final class SortJava {
    public static void main(String[] args) {
        List<Long> values = new ArrayList<>(200_000);
        for (long i = 0; i < 200_000L; i++) {
            values.add((i * 2654435761L) % 4294967296L);
        }
        values.sort(null);
        System.out.println(values.get(0));
        System.out.println(values.get(199_999));
    }
}
