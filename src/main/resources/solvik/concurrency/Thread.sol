module solvik {
    module concurrency {
        pub type Thread native "java.lang.Thread" {
            static pub func start(task: Task) -> Thread native "startVirtualThread"

            pub func await() native "join"
            pub func await(milliseconds: Long) native "join"
            pub func interrupt()
            pub func isAlive() -> Boolean
            pub func isInterrupted() -> Boolean
        }
    }
}
