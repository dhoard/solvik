package invalidrootsuper

class Main {
    pub static run(args: String...): Int {
        value: Main = Self { super: Main::new() }
        return 0
    }

    pub static new(): Self {
        return Self {}
    }
}
