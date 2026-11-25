# jsoup / junrar use reflection-free code, but keep their public API intact
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**
-keep class com.github.junrar.** { *; }
-dontwarn com.github.junrar.**
-dontwarn org.slf4j.**
-dontwarn javax.annotation.**
