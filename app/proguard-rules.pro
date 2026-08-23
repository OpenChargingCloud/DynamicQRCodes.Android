# Project specific ProGuard/R8 rules.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep line numbers in stack traces, but drop the original source file names.
# Together with the mapping file uploaded to the Play Console this turns
# obfuscated crash reports back into readable ones.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
