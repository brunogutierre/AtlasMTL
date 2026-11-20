package io.github.brunogutierre.atlasmtl.usage;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Turns a client address into a pseudonymous visitor id: an HMAC, so the address can't be recovered
 * by hashing every possible IPv4 address, as a plain hash would allow.
 */
@Component
public class Visitors {

  private final SecretKeySpec key;

  public Visitors(UsageProperties properties) {
    byte[] secret =
        properties.visitorKey().isBlank()
            ? new SecureRandom().generateSeed(32)
            : properties.visitorKey().getBytes(StandardCharsets.UTF_8);
    this.key = new SecretKeySpec(secret, "HmacSHA256");
  }

  public String id(String address) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(key);
      byte[] digest = mac.doFinal(address.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest, 0, 16);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }
}
