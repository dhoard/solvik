package reference

// SetProgramArgs records the CLI arguments after the source file.
func SetProgramArgs(args []string) {
	programArgs = append([]string{}, args...)
}
