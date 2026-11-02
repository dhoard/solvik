module solvik {
    module concurrency {
        pub trait Task native "java.lang.Runnable" {
            pub func run()
        }
    }
}
