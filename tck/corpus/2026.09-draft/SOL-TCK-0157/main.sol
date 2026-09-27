func guard() {
    try {
        print("trying")
    } catch (e: Exception) {
        print("root")
    } catch (e: RuntimeException) {
        print("runtime")
    }
}
guard()

print("EXECUTED-INVALID")
