public final class MatchJava {
    sealed interface Shape permits Circle, Square {}
    record Circle(double r) implements Shape {}
    record Square(double side) implements Shape {}

    static double area(Shape shape) {
        if (shape instanceof Circle c) {
            return Math.PI * c.r() * c.r();
        }
        if (shape instanceof Square s) {
            return s.side() * s.side();
        }
        throw new AssertionError("non-exhaustive match");
    }

    public static void main(String[] args) {
        double total = 0;
        for (long i = 0; i < 5_000_000L; i++) {
            total += area(new Circle(i));
        }
        System.out.println(total);
    }
}
