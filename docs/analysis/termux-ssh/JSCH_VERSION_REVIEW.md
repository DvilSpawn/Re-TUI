# JSch 2.28.0 to 2.28.8 source comparison

Primary upstream compare: https://github.com/mwiede/jsch/compare/jsch-2.28.0...jsch-2.28.8
Latest tag reviewed: https://github.com/mwiede/jsch/releases/tag/jsch-2.28.8

Compared 172 commits using GitHub compare API. This is a source review, not a claim of a runtime corruption fix.

The outgoing Session.encode and Session._write encryption, MAC, socket flush, and sequence-number update paths are unchanged. AES128CTR, AESGCM/AES128GCM, and HMAC implementation files are unchanged. Session changes add negotiated algorithm getters and exit-signal parsing. The only jce implementation change is finite-group DH public-key bounds enforcement. Other changes include known_hosts marker/certificate handling and parser bounds checks.

AES128GCM uses only JCE Cipher AES/GCM/NoPadding, GCMParameterSpec, Cipher.updateAAD, SecretKeySpec, and ByteBuffer operations. GCMParameterSpec and updateAAD are available from Android API19. No external crypto provider is required. Android API23 real-wrapper instrumentation passed: three consecutive same-buffer offset4 packets, AAD, nonce changes, and modified ciphertext rejection. Full crypto/terminal run: OK (4 tests), recorded in evidence/release-compat/api23-gcm-diagnostic.log. GCM remains diagnostic coverage; the production profile uses AES128CTR.

An upgrade can incorporate current parser/security maintenance; it does not explain or fix the observed CTR packet corruption according to this source comparison.

The candidate dependency was updated to 2.28.8. Maven Central JAR SHA-256: `2afca1357d816028f2ac33f22407d2488a68406682973949cebb1b8ea6688a8b`. Its POM declares no transitive dependencies; the base classes remain Java8 with optional multi-release implementations. Bundled JZlib and jBCrypt upstream notices are also retained.
