package vis

// Fields are always private; behavior is exposed through methods.

struct Account {

    secret: Long
    shield: Long
    balanceValue: Long

    public static func new(): Self {
        return Self { secret: 1, shield: 2, balanceValue: 100, }
    }

    // private method (omitted visibility)
    func internal(self): Long {
        return self.secret
    }

    public func getBalance(self): Long {
        return self.balanceValue
    }
}

struct Main {

    public static func run(args: String...): Long {
        let a: Account = Account.new()
        // public method accessible
        if a.getBalance() != 100 { return 2 }
        System.getOut().println("ok")
        return 0
    }
}
