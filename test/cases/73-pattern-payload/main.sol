package payloadpat

enum Shape {

    circle(Long)
    named(String)
}

struct Main {

    public static func run(args: String...): Long {
        let c: Shape = Shape.circle(5)
        match c {
            Shape.circle(5) => System.getOut().println("five")
            _ => System.getOut().println("not five")
        }
        let s: Shape = Shape.named("circle")
        match s {
            Shape.named("square") => System.getOut().println("square")
            Shape.named("circle") => System.getOut().println("circle")
            _ => System.getOut().println("other")
        }
        return 0
    }
}
