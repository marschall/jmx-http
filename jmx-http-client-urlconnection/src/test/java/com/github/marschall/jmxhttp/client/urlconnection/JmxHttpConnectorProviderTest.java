package com.github.marschall.jmxhttp.client.urlconnection;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;

import javax.management.remote.JMXServiceURL;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class JmxHttpConnectorProviderTest {
  
  private final String serviceUrl;
  private final String expected;

  public JmxHttpConnectorProviderTest(String serviceUrl, String url) {
    this.serviceUrl = serviceUrl;
    this.expected = url;
  }
  
  @Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(
        new Object[]{"service:jmx:http://localhost:8080/jmx-http", "http://localhost:8080/jmx-http"},
        new Object[]{"service:jmx:http://localhost/jmx-http", "http://localhost/jmx-http"},
        new Object[]{"service:jmx:http://localhost/", "http://localhost/"},
        new Object[]{"service:jmx:http://localhost", "http://localhost"}
    );
  }

  @Test
  public void getUrl() throws IOException {
    JMXServiceURL serviceURL = new JMXServiceURL(this.serviceUrl);
    URL actual = JmxHttpConnectorProvider.getUrl(serviceURL);
    assertEquals(new URL(this.expected), actual);
  }

}
