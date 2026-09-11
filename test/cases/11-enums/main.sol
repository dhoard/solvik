package enums

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
            Color.red => System.out().println("red")
            Color.green => System.out().println("green")
            Color.blue(r) => System.out().println("blue " .. r)
            _ => System.out().println("?")
        }
        match d {
            Color.blue(r) => System.out().println("got " .. r)
            _ => System.out().println("not blue")
        }
        let e: Color = Color.green
        match e {
            Color.red => System.out().println("red")
            Color.green => System.out().println("green")
            Color.blue(r) => System.out().println("blue")
            _ => System.out().println("?")
        }
        return 0
    }
}
