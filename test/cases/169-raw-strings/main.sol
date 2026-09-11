package test

class Main {
    public static run(args: String...): Long {
        System.out().println(r"back\slash \n tab")
        System.out().println(r#"quote " inside"#)
        System.out().println(r##"one # two ## three"##)
        System.out().println(r#"
    multi
line
"#)
        System.out().println("normal \"escaped\" string")
        return 0
    }
}
