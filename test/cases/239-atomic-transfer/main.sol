package atomictransfer

// The acceptance example: static Bank.transfer takes exclusive monitor
// ownership of both accounts for the duration of one atomic block.

struct Account {

    var balanceValue: Integer

    pub func new(balance: Integer): Self {
        return Self {
            balanceValue: balance,
        }
    }

    pub func balance(self): Integer {
        return self.balanceValue
    }

    pub func deposit(self, amount: Integer) {
        self.balanceValue += amount
    }

    pub func withdraw(self, amount: Integer) {
        self.balanceValue -= amount
    }
}

struct Bank {

    pub func transfer(
        source: Account,
        destination: Account,
        amount: Integer,
    ): Boolean {
        atomic(source, destination) {
            if source.balance() < amount {
                return false
            }

            source.withdraw(amount)
            destination.deposit(amount)
            return true
        }
    }
}

struct Main {

    pub func run(args: String...): Integer {
        let source: Account = Account.new(100)
        let destination: Account = Account.new(0)
        System.getOut().println(Bank.transfer(source, destination, 30))
        System.getOut().println(source.balance())
        System.getOut().println(destination.balance())
        System.getOut().println(Bank.transfer(source, destination, 1000))
        System.getOut().println(source.balance())
        System.getOut().println(destination.balance())

        // Ordinary reference semantics are unchanged: both names refer to the
        // same instance, and the atomic target set is identity-deduplicated.
        let alias: Account = source
        atomic(source, alias, source) {
            alias.deposit(5)
        }
        System.getOut().println(source.balance())
        return 0
    }
}
