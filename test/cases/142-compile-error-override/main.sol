package useoverride

interface Named { name(): String }

class Person implements Named {
    override public name(): String { return "x" }
}

class Main { public static run(args: String...): Long { return 0 } }
