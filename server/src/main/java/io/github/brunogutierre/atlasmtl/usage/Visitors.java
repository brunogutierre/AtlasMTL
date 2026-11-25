package io.github.brunogutierre.atlasmtl.usage;

import jakarta.servlet.http.HttpServletRequest;
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
 *
 * <p>Behind Cloudflare (as on Render), the connection comes from a proxy and {@code
 * X-Forwarded-For} ends with a public Cloudflare edge address that Tomcat doesn't trust, so many
 * visitors would share one id. {@code atlas.usage.client-ip-header} names the header the edge
 * overwrites with the visitor's address instead.
 */
@Component
public class Visitors {

  private final SecretKeySpec key;
  private final String clientIpHeader;

  public Visitors(UsageProperties properties) {
    this.clientIpHeader = properties.clientIpHeader();
    byte[] secret =
        properties.visitorKey().isBlank()
            ? new SecureRandom().generateSeed(32)
            : properties.visitorKey().getBytes(StandardCharsets.UTF_8);
    this.key = new SecretKeySpec(secret, "HmacSHA256");
  }

  /** The visitor id of a request. */
  public String id(HttpServletRequest request) {
    return id(address(request));
  }

  /** The visitor's address: the configured edge header when present, else the connection's. */
  String address(HttpServletRequest request) {
    if (!clientIpHeader.isEmpty()) {
      String header = request.getHeader(clientIpHeader);
      if (header != null && !header.isBlank()) {
        return header.split(",", 2)[0].strip();
      }
    }
    return request.getRemoteAddr();
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
