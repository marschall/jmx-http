package com.github.marschall.jmxhttp.client.urlconnection;

import java.io.IOException;
import java.net.URL;
import java.util.Map;

import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorProvider;
import javax.management.remote.JMXServiceURL;

public class JmxHttpConnectorProvider implements JMXConnectorProvider {

  @Override
  public JMXConnector newJMXConnector(JMXServiceURL serviceURL, Map<String, ?> environment) throws IOException {
    return new JmxHttpConnector(getUrl(serviceURL));
  }
  
  private static URL getUrl(JMXServiceURL serviceURL) {
    return null;
  }

}
