# These entry names are used in detached shell commands as well as the manifest.
-keep class com.svenuks.imsforpixel.BrokerInstrumentation { *; }
-keep class com.svenuks.imsforpixel.GuardianInstrumentation { *; }
-keep class com.svenuks.imsforpixel.MainActivity { public <init>(); }
-keep class com.svenuks.imsforpixel.GuardianService { public <init>(); }
-keep class com.svenuks.imsforpixel.GuardianStopReceiver { public <init>(); }

# ART/Unsafe mirror layout is sensitive to changes; this is a small compatibility helper.
-keep class org.lsposed.hiddenapibypass.** { *; }

# Bouncy Castle registers JCA implementations using concatenated class-name strings.
# Preserve these dynamic provider classes, not the entire crypto library.
-keep,allowoptimization class org.bouncycastle.**.provider.** { *; }

# Remove debug-only log formatting; retain warnings and errors for real failures.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}

# BC includes optional LDAP/CRL paths from desktop Java. Android never supplied JNDI;
# local ADB certificate/pairing code does not call those paths. Suppress only these
# known absent platform types, not arbitrary missing crypto or application classes.
-dontwarn javax.naming.NamingEnumeration
-dontwarn javax.naming.NamingException
-dontwarn javax.naming.directory.Attribute
-dontwarn javax.naming.directory.Attributes
-dontwarn javax.naming.directory.DirContext
-dontwarn javax.naming.directory.InitialDirContext
-dontwarn javax.naming.directory.SearchControls
-dontwarn javax.naming.directory.SearchResult
