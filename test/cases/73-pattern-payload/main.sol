package payloadpat

enum Shape {
    Circle(Int)
    Named(String)
}

class Main {
    pub static run(args: String...): Int {
        c: Shape = Shape::Circle(5)
        match c {
            Shape::Circle(5) => stdout.println("five")
            _ => stdout.println("not five")
        }
        s: Shape = Shape::Named("circle")
        match s {
            Shape::Named("square") => stdout.println("square")
            Shape::Named("circle") => stdout.println("circle")
            _ => stdout.println("other")
        }
        return 0
    }
}
