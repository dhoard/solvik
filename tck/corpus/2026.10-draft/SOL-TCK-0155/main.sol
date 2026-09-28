class ConfigError extends ApplicationException {
}
func guard() {
    try {
        throw ConfigError("no config")
    } catch (e: ApplicationException) {
        print("[" .. e.getMessage() .. "]")
    }
}
guard()
