module invalidrootsuper

class Main {

    public static run(args: String...): Long {
        value: Main = Self { super: Main.new(), }
        return 0
    }

    public static new(): Self {
        return Self {}
    }
}
