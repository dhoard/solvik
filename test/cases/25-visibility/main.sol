package vis

class Account {
    secret: Int
    protected shield: Int
    pub balance: Int

    pub static new(): Self {
        return Self { secret: 1, shield: 2, balance: 100 }
    }

    // private method (omitted visibility)
    internal(): Int {
        return secret
    }

    pub getBalance(): Int {
        return balance
    }
}

class Main {
    pub static run(args: String...): Int {
        a: Account = Account::new()
        // pub field accessible
        if a.balance != 100 { return 1 }
        // pub method accessible
        if a.getBalance() != 100 { return 2 }
        stdout.println("ok")
        return 0
    }
}
