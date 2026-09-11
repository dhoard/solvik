package namingrules

interface lowerInterface {
    BadMethod(BadParam: Long): Long
}

enum lowerEnum {
    BadVariant
}

class lowerClass {
    BadField: Long

    public BadMethod(BadParam: Long): Long {
        BadLocal: Long = BadParam
        for BadItem in [1] {}
        try { throw "error" } catch (BadError) {}
        return BadLocal
    }
}

class Main {
    public static run(args: String...): Long {
        return 0
    }
}
