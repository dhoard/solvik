// expected: SOLV-SEM-055
class AppError extends RuntimeException {
}
func run(): Unit {
    try {
    }

    catch (e: RuntimeException) {
    }

    catch (e2: AppError) {
    }
}
