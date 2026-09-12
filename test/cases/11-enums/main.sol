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
            Color.red => System.getOut().println("red")
            Color.green => System.getOut().println("green")
            Color.blue(r) => System.getOut().println("blue " .. r)
            _ => System.getOut().println("?")
        }
        match d {
            Color.blue(r) => System.getOut().println("got " .. r)
            _ => System.getOut().println("not blue")
        }
        let e: Color = Color.green
        match e {
            Color.red => System.getOut().println("red")
            Color.green => System.getOut().println("green")
            Color.blue(r) => System.getOut().println("blue")
            _ => System.getOut().println("?")
        }
        return 0
    }
}
