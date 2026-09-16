# Standalone build

Run `../build.sh` for a clean JVM distribution and `../build-native.sh` for a clean native distribution. Both wrappers select GraalVM from `/opt/graalvm` and run the Maven `clean` and `package` goals.

During Phase 0 the generated launcher names and behavior are inherited migration inputs. They are used only for baseline verification and are not a supported compatibility contract. Phase 5 replaces the exposed launcher and language registration with Solvik.
