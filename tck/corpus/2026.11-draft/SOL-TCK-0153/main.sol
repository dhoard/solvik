class Note {
    var message: String = "fine"

    method getMessage(): String {
        return this.message
    }
}
var n: Note = Note()
print(n.message)
print(n.getMessage())
