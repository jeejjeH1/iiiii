# org.json and the app's own code are accessed directly; nothing reflective to keep.
-dontwarn org.slf4j.**

# Google sign-in through Credential Manager (its Play services side is found by reflection)
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** {
  *;
}
