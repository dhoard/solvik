package payloadpat

enum Shape {

    circle(Long)
    named(String)
}

class Main {

    public static run(args: String...): Long {
        let c: Shape = Shape.circle(5)
        match c {
            Shape.circle(5) => stdout.println("five")
            _ => stdout.println("not five")
        }
        let s: Shape = Shape.named("circle")
        match s {
            Shape.named("square") => stdout.println("square")
            Shape.named("circle") => stdout.println("circle")
            _ => stdout.println("other")
        }
        return 0
    }
}
