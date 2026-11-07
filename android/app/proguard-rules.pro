# R8 rules for Reflux's Android release (docs/RELEASE.md, "Android release build").
#
# Reflux's own code uses no reflection, no runtime class discovery, and no annotation-driven serialization: JSON is
# read through kotlinx.serialization's JsonElement tree, the database through SQLDelight's generated code, and
# libmpv is desktop-only. What needs keeping is third-party, and each rule says why.

# smbj (SMB shares) delivers its events through MBassador, which finds handler methods by their @Handler
# annotation at run time, and negotiates with Bouncy Castle's digests and ciphers by class.
-keep class com.hierynomus.** { *; }
-keep class net.engio.mbassy.** { *; }
-keepclassmembers class * {
    @net.engio.mbassy.listener.Handler *;
}
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
-dontwarn javax.naming.**
# MBassador's optional expression-language filters (javax.el), which smbj does not use.
-dontwarn javax.el.**
-dontwarn org.ietf.jgss.**
-dontwarn com.sun.jna.**

# The engine modules are plain JVM libraries shared with the desktop. Their desktop-only classes (the JDBC
# database opener, the JDK HTTP client, the desktop artwork cache's file APIs) are never reached on Android; the
# JDK types they name do not exist there.
-dontwarn java.net.http.**
-dontwarn java.awt.**
-dontwarn javax.swing.**
-dontwarn app.cash.sqldelight.driver.jdbc.**
-dontwarn org.sqlite.**

# Logging facades some libraries name and Reflux does not ship a binding for.
-dontwarn org.slf4j.**

# Keep stack traces useful in reports: line numbers stay, file names are folded into one.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
