package enums

enum Color {
    Red
    Green
    Blue(Int)
}

class Main {
    pub static run(args: String...): Int {
        c: Color = Color::Red
        d: Color = Color::Blue(255)
        match c {
            Color::Red => stdout.println("red")
            Color::Green => stdout.println("green")
            Color::Blue(r) => stdout.println("blue " .. r)
            _ => stdout.println("?")
        }
        match d {
            Color::Blue(r) => stdout.println("got " .. r)
            _ => stdout.println("not blue")
        }
        e: Color = Color::Green
        match e {
            Color::Red => stdout.println("red")
            Color::Green => stdout.println("green")
            Color::Blue(r) => stdout.println("blue")
            _ => stdout.println("?")
        }
        return 0
    }
}
