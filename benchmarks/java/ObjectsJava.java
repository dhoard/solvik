public final class ObjectsJava {
    static final class Point {
        final long x;
        final long y;

        Point(long x, long y) {
            this.x = x;
            this.y = y;
        }

        long dist2(Point other) {
            long dx = x - other.x;
            long dy = y - other.y;
            return dx * dx + dy * dy;
        }
    }

    public static void main(String[] args) {
        long total = 0;
        long i = 0;
        while (i < 5_000_000L) {
            Point p = new Point(i, i + 1);
            Point q = new Point(i + 2, i + 3);
            total += p.dist2(q);
            i++;
        }
        System.out.println(total);
    }
}
