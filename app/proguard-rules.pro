# Add project specific ProGuard / R8 rules here.

# Preserve Kotlin attributes and metadata for R8
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable
-dontwarn kotlin.**
-dontwarn kotlinx.**

