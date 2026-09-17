# Standalone distribution

Run `../build.sh` for a clean JVM distribution and `../build-native.sh` for a clean native
distribution. Both wrappers select GraalVM from `/opt/graalvm` and run the Maven `clean` and
`package` goals.

The JVM build copies the module jars into `standalone/target/modules/` and renders the
`standalone/solvik` launcher template into `standalone/target/solvik`. The native profile invokes
`native-image` and produces `standalone/target/solviknative`.

Both launchers accept a `.sol` source file and expose only the `solvik` language id:

```bash
JAVA_HOME=/opt/graalvm ./standalone/target/solvik language/tests/Hello.sol
JAVA_HOME=/opt/graalvm ./standalone/target/solviknative language/tests/Hello.sol
```

The launcher templates are supported release inputs; they are checked into the repository and are
not inherited migration artifacts.
