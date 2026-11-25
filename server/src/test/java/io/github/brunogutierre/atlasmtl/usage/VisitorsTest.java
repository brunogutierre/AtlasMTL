package io.github.brunogutierre.atlasmtl.usage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class VisitorsTest {

  private static Visitors visitors(String clientIpHeader) {
    return new Visitors(new UsageProperties(null, null, "key", clientIpHeader));
  }

  private static MockHttpServletRequest request(String remoteAddr, String cfConnectingIp) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr(remoteAddr);
    if (cfConnectingIp != null) {
      request.addHeader("CF-Connecting-IP", cfConnectingIp);
    }
    return request;
  }

  @Test
  void usesTheConnectionAddressByDefault() {
    Visitors visitors = visitors("");

    assertThat(visitors.address(request("104.22.17.40", "198.51.100.7"))).isEqualTo("104.22.17.40");
  }

  @Test
  void separatesVisitorsBehindTheSameEdgeWhenTheHeaderIsConfigured() {
    Visitors visitors = visitors("CF-Connecting-IP");

    String first = visitors.id(request("104.22.17.40", "198.51.100.7"));
    String second = visitors.id(request("104.22.17.40", "198.51.100.8"));

    assertThat(first).isNotEqualTo(second).isEqualTo(visitors.id("198.51.100.7"));
  }

  @Test
  void fallsBackToTheConnectionAddressWhenTheHeaderIsMissing() {
    Visitors visitors = visitors("CF-Connecting-IP");

    assertThat(visitors.address(request("10.0.0.5", null))).isEqualTo("10.0.0.5");
    assertThat(visitors.address(request("10.0.0.5", "  "))).isEqualTo("10.0.0.5");
  }

  @Test
  void takesTheFirstAddressOfAList() {
    Visitors visitors = visitors("CF-Connecting-IP");

    assertThat(visitors.address(request("10.0.0.5", " 198.51.100.7 , 10.0.0.9")))
        .isEqualTo("198.51.100.7");
  }
}
