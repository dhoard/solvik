package payloadpat

enum Shape {

    circle(Long)
    named(String)
}

class Main {

    public static run(args: String...): Long {
        let c: Shape = Shape.circle(5)
        match c {
            Shape.circle(5) => System.out().println("five")
            _ => System.out().println("not five")
        }
        let s: Shape = Shape.named("circle")
        match s {
            Shape.named("square") => System.out().println("square")
            Shape.named("circle") => System.out().println("circle")
            _ => System.out().println("other")
        }
        return 0
    }
}
