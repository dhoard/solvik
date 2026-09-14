package namingrules

trait lowerInterface {
    func BadMethod(self, BadParam: Long): Long
}

enum lowerEnum {
    BadVariant
}

struct lowerStruct {
    BadField: Long

    public func BadMethod(self, BadParam: Long): Long {
        let BadLocal: Long = BadParam
        for BadItem in [1] {}
        try { throw Exception.new("error") } catch (e: BadError) {}
        return BadLocal
    }
}

struct Main {
    public func run(args: String...): Integer {
        return 0
    }
}
