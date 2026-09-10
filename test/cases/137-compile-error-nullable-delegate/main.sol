module nullableadelegate

interface Named { name(): String }

class Wrapper implements Named {
    person: Named?
    delegate Named to person
}

class Main { public static run(args: String...): Long { return 0 } }
