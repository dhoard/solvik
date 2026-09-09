package bad_trailing_case_comma

class Main {
    pub static run(args: String...): Int {
        switch 1 {
            case 1, 2,: {}
        }
        return 0
    }
}
