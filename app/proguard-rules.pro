# Readium uses reflection for serialization of locators and parcelables.
-keep class org.readium.** { *; }
-dontwarn org.readium.**
-keep class com.shockwave.** { *; }
-keep class com.github.barteksc.** { *; }
-keep class io.legere.** { *; }
-dontwarn com.github.barteksc.**
# sherpa-onnx's native code reads its Kotlin config classes field by field and calls back into Kotlin.
-keep class com.k2fsa.sherpa.onnx.** { *; }
