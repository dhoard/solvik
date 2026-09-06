// expected C110: Channel<T> was removed in Phase 14; use Thread/Mutex/Process
package removed_channel_type

func main() -> Int {
    c: Channel<Int> = channel()
    return 0
}
