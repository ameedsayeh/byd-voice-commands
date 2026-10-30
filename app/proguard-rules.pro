# BYD framework is provided by the device at runtime; never warn about the stubs.
-dontwarn android.hardware.bydauto.**
-keep class android.hardware.bydauto.** { *; }

# Subclass of the framework's AbsBYDAutoDevice (generic set path) — keep its overrides.
-keep class com.offline.bydvoice.byd.GenericBydDevice { *; }
-dontwarn android.hardware.**

# Moonshine: the native library builds and reads these classes by name.
-keep class ai.moonshine.voice.JNI { *; }
-keep class ai.moonshine.voice.Transcript { *; }
-keep class ai.moonshine.voice.TranscriptLine { *; }
-keep class ai.moonshine.voice.TranscriberOption { *; }
-keep class ai.moonshine.voice.WordTiming { *; }
-keep class ai.moonshine.voice.SpeakerSpan { *; }
-keep class ai.moonshine.voice.SpeechClip { *; }
-keep class ai.moonshine.voice.Tts* { *; }
# Its downloader / UI helpers need libraries we exclude (we bundle the models).
-dontwarn ai.moonshine.voice.**
-dontwarn okhttp3.**
-dontwarn androidx.work.**
