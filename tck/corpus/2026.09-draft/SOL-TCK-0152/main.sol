class Bad extends RuntimeException {
    func getMessage(): String {
        return "mine"
    }
}

print("EXECUTED-INVALID")
