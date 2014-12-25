package com.github.marschall.jmxhttp.client.urlconnection;

import java.io.IOException;

import javax.management.ListenerNotFoundException;
import javax.management.MBeanServerConnection;
import javax.management.NotificationListener;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

public class Integration {

  public static void main(String[] args) throws IOException, ListenerNotFoundException {
    JMXServiceURL serviceURL = new JMXServiceURL("service:jmx:http://localhost:8080/jmx-http");
    try (JMXConnector connector = JMXConnectorFactory.newJMXConnector(serviceURL, null)) {
      connector.connect();
      NotificationListener listener = (notification, handback) -> {};
      connector.addConnectionNotificationListener(listener, null, null);
      MBeanServerConnection connection = connector.getMBeanServerConnection();
      System.out.println(connection.getMBeanCount());
      System.out.println(connection.getDefaultDomain());
      connector.removeConnectionNotificationListener(listener);
    }

  }

}
