class Note {
    val message: String = "fine"

    func getMessage(): String {
        return this.message
    }
}
val n = Note()
print(n.message)
print(n.getMessage())
