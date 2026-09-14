package listsort

struct Main {

    public func run(args: String...): Integer {
        let values: List<Long> = [100, 9, 21, 3]
        values.sort()
        System.getOut().println(values)
        let mixed: List<Double> = [3.5, -1.25, 0.0, 100.75]
        mixed.sort()
        System.getOut().println(mixed)
        let text: List<String> = ["pear", "apple", "kiwi"]
        text.sort()
        System.getOut().println(text)
        return 0
    }
}
