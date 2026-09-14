package vis

// Fields are always private; behavior is exposed through methods.

struct Account {

    secret: Long
    shield: Long
    balanceValue: Long

    pub func new(): Self {
        return Self { secret: 1, shield: 2, balanceValue: 100, }
    }

    // private method (omitted visibility)
    func internal(self): Long {
        return self.secret
    }

    pub func getBalance(self): Long {
        return self.balanceValue
    }
}

struct Main {

    pub func run(args: String...): Integer {
        let a: Account = Account.new()
        // pub method accessible
        if a.getBalance() != 100 { return 2 }
        System.getOut().println("ok")
        return 0
    }
}
