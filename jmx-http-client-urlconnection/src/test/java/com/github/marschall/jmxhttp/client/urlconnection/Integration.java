package com.github.marschall.jmxhttp.client.urlconnection;

import java.io.IOException;
import java.util.ServiceLoader;

import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXConnectorProvider;
import javax.management.remote.JMXServiceURL;

public class Integration {

  public static void main(String[] args) throws IOException {
    ServiceLoader<JMXConnectorProvider> serviceLoader = ServiceLoader.load(JMXConnectorProvider.class, Thread.currentThread().getContextClassLoader());
    for (JMXConnectorProvider jmxConnectorProvider : serviceLoader) {
      System.out.println(jmxConnectorProvider);
    }
    
    JMXServiceURL serviceURL = new JMXServiceURL("service:jmx:http://localhost:8080/jmx-http");
    try (JMXConnector connector = JMXConnectorFactory.newJMXConnector(serviceURL, null)) {
      connector.connect();
      MBeanServerConnection connection = connector.getMBeanServerConnection();
      System.out.println(connection.getMBeanCount());
    }

  }

}
