package enummissingpayload

enum E {

    value(Long)
}

class Main {

    public static run(args: String...): Long {
        let value: E = E.value()
        return 0
    }
}
