# Keep accessibility service entry point.
-keep class com.quqi.autohand.service.AutoHandService { *; }

# ========== 友盟统计SDK混淆规则 ==========
-keep class com.umeng.** {*;}
-keep class org.repackage.** {*;}
-keep class com.uyumao.** { *; }
-keepclassmembers class * {
   public <init> (org.json.JSONObject);
}

# ========== 友盟APM SDK混淆规则 ==========
-keep class com.uc.** { *; }
-keep class com.efs.** { *; }
-keepclassmembers class * {
    public <init>(org.json.JSONObject);
}
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
