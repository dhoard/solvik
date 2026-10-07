class Bad extends RuntimeException {
    method getMessage(): String {
        return "mine"
    }
}

print("EXECUTED-INVALID")
