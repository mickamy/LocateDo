# R8 repackaged a part of a Kotlin multifile facade (StringsKt__StringBuilderKt) out of kotlin.text while its
# package-private parent part stayed behind, which crashed release builds with IllegalAccessError at startup.
# Keep the facades and their parts in their own packages; they may still be renamed, shrunk, and optimized.
-keep,allowshrinking,allowoptimization,allowobfuscation class kotlin.**Kt
-keep,allowshrinking,allowoptimization,allowobfuscation class kotlin.**Kt__*
