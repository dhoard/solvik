module enums

enum Color {

    red
    green
    blue(Long)
}

class Main {

    public static run(args: String...): Long {
        let c: Color = Color.red
        let d: Color = Color.blue(255)
        match c {
            Color.red => stdout.println("red")
            Color.green => stdout.println("green")
            Color.blue(r) => stdout.println("blue " .. r)
            _ => stdout.println("?")
        }
        match d {
            Color.blue(r) => stdout.println("got " .. r)
            _ => stdout.println("not blue")
        }
        let e: Color = Color.green
        match e {
            Color.red => stdout.println("red")
            Color.green => stdout.println("green")
            Color.blue(r) => stdout.println("blue")
            _ => stdout.println("?")
        }
        return 0
    }
}
