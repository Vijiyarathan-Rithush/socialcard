package com.rvi.nativeimage;

import com.oracle.svm.core.jdk.NativeLibrarySupport;
import com.oracle.svm.core.jdk.PlatformNativeLibrarySupport;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.c.NativeLibraries;
import java.lang.reflect.Method;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.hosted.Feature;

/** Links the JDK PC/SC bridge into the Windows executable without a JVM or a JNI sidecar. */
public final class PcscNativeFeature implements Feature
{
    private static final String LIBRARY = "j2pcsc";

    @Override
    public boolean isInConfiguration(final IsInConfigurationAccess access)
    {
        return Platform.includedIn(Platform.WINDOWS.class);
    }

    @Override
    public void beforeAnalysis(final BeforeAnalysisAccess access)
    {
        // System.loadLibrary("j2pcsc") must initialize this built-in library at run time.
        NativeLibrarySupport.singleton().preregisterUninitializedBuiltinLibrary(LIBRARY);
        registerNativePrefix();

        final NativeLibraries libraries = ((FeatureImpl.BeforeAnalysisAccessImpl) access).getNativeLibraries();
        libraries.addStaticJniLibrary(LIBRARY);
        libraries.addDynamicNonJniLibrary("winscard");
    }

    private static void registerNativePrefix()
    {
        try
        {
            // The hosted API renamed this method between GraalVM 25 update streams.
            final Method registration;
            try
            {
                registration = PlatformNativeLibrarySupport.class.getMethod("addBuiltinNativePrefix", String.class);
            }
            catch (NoSuchMethodException exception)
            {
                PlatformNativeLibrarySupport.class.getMethod("addBuiltinPkgNativePrefix", String.class)
                        .invoke(PlatformNativeLibrarySupport.singleton(), "sun_security_smartcardio_PCSC");
                return;
            }
            registration.invoke(PlatformNativeLibrarySupport.singleton(), "sun_security_smartcardio_PCSC");
        }
        catch (ReflectiveOperationException exception)
        {
            throw new IllegalStateException("This GraalVM cannot register the static PC/SC JNI library.", exception);
        }
    }
}
