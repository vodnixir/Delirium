# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in proguard-android-optimize.txt, located in the SDK tools directory.

# Firebase uses reflection; kotlinx-serialization keeps own metadata.
-keep class dev.vodnixir.delirium.domain.model.** { *; }
