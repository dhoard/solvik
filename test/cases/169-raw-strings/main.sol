package test

struct Main {
    public func run(args: String...): Long {
        System.getOut().println(r"back\slash \n tab")
        System.getOut().println(r#"quote " inside"#)
        System.getOut().println(r##"one # two ## three"##)
        System.getOut().println(r#"
    multi
line
"#)
        System.getOut().println("normal \"escaped\" string")
        return 0
    }
}
