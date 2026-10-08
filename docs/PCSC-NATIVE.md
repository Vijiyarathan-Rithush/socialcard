# PC/SC in the Windows native build

The native application keeps the JDK `javax.smartcardio` API and the `SunPCSC`
provider. It does not replace the repository interface or call a separate JVM.

The metadata in `META-INF/native-image/com.rvi/pcsc/` does three things:

- Includes the `java.smartcardio` module and the provider constructors used by
  the JCA service lookup and the JDK fallback lookup.
- Defers `TerminalFactory`, `PlatformPCSC`, `PCSC`, and `PCSCTerminals`
  initialization until the executable runs. The selected factory, loaded
  library state, and Windows smart-card context must belong to the running
  process, not the image builder.
- Registers the JNI callbacks from OpenJDK's `pcsc.c`: `PCSCException(int)`,
  `OutOfMemoryError(String)`, and the `String` class used for reader arrays.

`SunPCSC` itself is deliberately not forced to initialize at run time: GraalVM's
security-service support controls provider initialization and registration.

## Native library

The native Maven profile compiles `src/native/java` and activates
`com.rvi.nativeimage.PcscNativeFeature`. This build-only feature links the
GraalVM JDK's `lib/static/windows-amd64/j2pcsc.lib`, registers its JNI symbol
prefix, and makes `System.loadLibrary("j2pcsc")` initialize the built-in library.
It links the Windows `winscard` import library. The ordinary JVM build has no
dependency on these GraalVM build APIs.

Use the static archive from the **same GraalVM JDK used to build the executable**.
The archive must provide the static `JNI_OnLoad_j2pcsc` entry point with a JNI
version supported for static libraries (at least JNI 1.8). Preserve the JDK's
applicable notices in the distribution.

On Windows this bridge calls `WinSCard.dll`, supplied by Windows, and the
Microsoft C runtime. Check the final executable's PE import table and include
its redistributable runtime dependencies when needed. The PC/SC bridge does not
start Java or require a JRE. JavaFX's optional Java2D image fallback can make
Native Image emit AWT support DLLs and small `java.dll`/`jvm.dll` forwarding shims;
these are native libraries, not a JVM, and are included in the distribution.

If a different GraalVM distribution lacks the static archive, a separately
configured build may omit the feature and place its matching `j2pcsc.dll`
beside the executable. Native Image searches that directory for JNI libraries.
That is a fallback distribution strategy, not part of the static profile.

Do not use an empty result from `TerminalFactory.getDefault().terminals().list()`
as the only native smoke test. The default factory silently falls back to a
`None` provider when PC/SC initialization fails. A read-only diagnostic must
request `TerminalFactory.getInstance("PC/SC", null)` explicitly and report the
provider or the full initialization error. It may list readers, but must not
connect to cards or send APDUs.

## Sources

- [OpenJDK 25 PC/SC JNI bridge](https://github.com/openjdk/jdk25u/blob/master/src/java.smartcardio/share/native/libj2pcsc/pcsc.c)
- [OpenJDK 25 Windows library initialization](https://github.com/openjdk/jdk25u/blob/master/src/java.smartcardio/windows/classes/sun/security/smartcardio/PlatformPCSC.java)
- [GraalVM JNI library loading](https://www.graalvm.org/latest/reference-manual/native-image/dynamic-features/JNI/)
- [GraalVM security-service registration](https://github.com/oracle/graal/blob/master/substratevm/src/com.oracle.svm.hosted/src/com/oracle/svm/hosted/SecurityServicesFeature.java)
