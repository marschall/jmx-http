package com.github.marschall.jmxhttp.client.urlconnection;

import java.io.IOException;
import java.net.URL;
import java.util.Map;

import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorProvider;
import javax.management.remote.JMXProviderException;
import javax.management.remote.JMXServiceURL;

/**
 * Connector provider for running JMX over HTTP.
 * <p>
 * This provider supports both {@code http} and {@code https} URLs.
 * Service urls must look like this: 
 * <a href="service:jmx:http://localhost:8080/jmx-http">service:jmx:http://localhost:8080/jmx-http</a>
 *
 */
public class JmxHttpConnectorProvider implements JMXConnectorProvider {

  @Override
  public JMXConnector newJMXConnector(JMXServiceURL serviceURL, Map<String, ?> environment) throws IOException {
    return new JmxHttpConnector(getUrl(serviceURL));
  }
  
  static URL getUrl(JMXServiceURL serviceURL) throws IOException {
    int port = serviceURL.getPort();
    if (port == 0) {
      port = -1;
    }
    String protocol = serviceURL.getProtocol();
    if (!"http".equals(protocol) && !"https".equals(protocol)) {
      throw new JMXProviderException("unsupported protocol: " + protocol);
    }
    return new URL(protocol, serviceURL.getHost(), port, serviceURL.getURLPath());
  }

}
