module vis

class Account {

    secret: Long
    protected shield: Long
    public balance: Long

    public static new(): Self {
        return Self { secret: 1, shield: 2, balance: 100, }
    }

    // private method (omitted visibility)
    internal(): Long {
        return self.secret
    }

    public getBalance(): Long {
        return self.balance
    }
}

class Main {

    public static run(args: String...): Long {
        a: Account = Account.new()
        // public field accessible
        if a.balance != 100 { return 1 }
        // public method accessible
        if a.getBalance() != 100 { return 2 }
        stdout.println("ok")
        return 0
    }
}
