# Preserve the standard SSH pane entry points and its reflectively loaded implementations.
-keep class ohi.andre.consolelauncher.managers.termux.TermuxWorkspaceSsh** { *; }
-keep class ohi.andre.consolelauncher.managers.termux.TermuxWorkspaceActivity** { *; }
# JSch reflects into package-private core factories; callers must stay in that package.
-keeppackagenames com.jcraft.jsch
# JSch selects these local-only SSH implementations by configured class name.
-keep class com.jcraft.jsch.UserAuthNone { public <init>(); }
-keep class com.jcraft.jsch.UserAuthPublicKey { <init>(); }
-keep class com.jcraft.jsch.DHEC256 { public <init>(); }
-keep class com.jcraft.jsch.jce.Random { public <init>(); }
-keep class com.jcraft.jsch.jce.KeyPairGenECDSA { public <init>(); }
-keep class com.jcraft.jsch.jce.SignatureECDSA256 { public <init>(); }
-keep class com.jcraft.jsch.jce.ECDHN { public <init>(); }
-keep class com.jcraft.jsch.jce.AES128CTR { public <init>(); }
-keep class com.jcraft.jsch.jce.HMACSHA1 { public <init>(); }
-keep class com.jcraft.jsch.jce.HMACSHA256 { public <init>(); }
-keep class com.jcraft.jsch.jce.SHA256 { public <init>(); }
-keep class com.jcraft.jsch.jce.SHA1 { public <init>(); }
-keep class com.jcraft.jsch.jce.MD5 { public <init>(); }
-keep class com.jcraft.jsch.jce.TripleDESCBC { public <init>(); }
