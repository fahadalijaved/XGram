// Checks the release keystore before the build, the way the Android build will use it:
//   XGRAM_KEYSTORE_PASSWORD opens the keystore, XGRAM_KEY_ALIAS names a private key in it, and
//   XGRAM_KEY_PASSWORD unlocks that key.
// keytool can't check the key password of a PKCS12 keystore: it ignores -keypass with a warning and succeeds.
// Loading the key with java.security.KeyStore does check it, for PKCS12 and JKS alike.
//
//   java scripts/ci/CheckSigningKey.java <keystore file>      (JDK 11+ runs a single source file directly)
//
// The passwords and the alias come only from the environment, never from the command line, and nothing secret is
// printed. Exit status: 0 = the key loads, 1 = a secret doesn't match the keystore, 2 = usage error.
import java.io.File;
import java.security.KeyStore;
import java.security.UnrecoverableKeyException;

public class CheckSigningKey {
  public static void main(String[] args) {
    if (args.length != 1) {
      fail(2, "usage: java CheckSigningKey.java <keystore file>");
    }
    String storePassword = System.getenv("XGRAM_KEYSTORE_PASSWORD");
    String alias = System.getenv("XGRAM_KEY_ALIAS");
    String keyPassword = System.getenv("XGRAM_KEY_PASSWORD");
    if (isEmpty(storePassword) || isEmpty(alias) || isEmpty(keyPassword)) {
      fail(2, "XGRAM_KEYSTORE_PASSWORD, XGRAM_KEY_ALIAS and XGRAM_KEY_PASSWORD must be set.");
    }
    KeyStore keyStore = null;
    try {
      // Detects the type (PKCS12 or JKS) from the file. Java 9+.
      keyStore = KeyStore.getInstance(new File(args[0]), storePassword.toCharArray());
    } catch (Exception e) {
      fail(1, "XGRAM_KEYSTORE_PASSWORD does not open the keystore, or XGRAM_KEYSTORE_BASE64 is not a PKCS12/JKS file ("
        + e.getClass().getSimpleName() + ").");
    }
    try {
      if (!keyStore.isKeyEntry(alias)) {
        fail(1, "The keystore has no private key named by XGRAM_KEY_ALIAS.");
      }
      if (keyStore.getKey(alias, keyPassword.toCharArray()) == null) {
        fail(1, "XGRAM_KEY_PASSWORD does not unlock the key named by XGRAM_KEY_ALIAS.");
      }
    } catch (UnrecoverableKeyException e) {
      fail(1, "XGRAM_KEY_PASSWORD does not unlock the key named by XGRAM_KEY_ALIAS.");
    } catch (Exception e) {
      fail(1, "Could not read the key named by XGRAM_KEY_ALIAS (" + e.getClass().getSimpleName() + ").");
    }
    System.err.println("Signing key check passed (" + keyStore.getType() + " keystore).");
  }

  private static boolean isEmpty(String value) {
    return value == null || value.isEmpty();
  }

  private static void fail(int status, String message) {
    System.err.println(message);
    System.exit(status);
  }
}
