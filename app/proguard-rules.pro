# jsoup / junrar use reflection-free code, but keep their public API intact
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**
-keep class com.github.junrar.** { *; }
-dontwarn com.github.junrar.**
-dontwarn org.slf4j.**
-dontwarn javax.annotation.**

# ИИ-движки: JNI и рефлексия
-keep class com.google.ai.edge.** { *; }
-keep class com.google.protobuf.** { *; }
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.** { *; }
-keep class com.google.gson.** { *; }
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod
-keepclasseswithmembernames class * { native <methods>; }
-dontwarn javax.lang.model.**
-dontwarn com.google.auto.value.**
-dontwarn org.checkerframework.**
-dontwarn com.google.errorprone.**
-dontwarn com.google.j2objc.**
-dontwarn kotlin.reflect.jvm.internal.**
# llama.cpp JNI: имена вызываются из нативного кода
-keep class com.nanopero.app.LlamaNative { *; }
-keep interface com.nanopero.app.LlamaNative$TokenSink { *; }
-keep class * implements com.nanopero.app.LlamaNative$TokenSink { *; }
