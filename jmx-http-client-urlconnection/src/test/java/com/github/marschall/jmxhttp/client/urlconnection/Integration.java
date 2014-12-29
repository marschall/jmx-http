package com.github.marschall.jmxhttp.client.urlconnection;

import java.io.IOException;

import javax.management.InstanceNotFoundException;
import javax.management.ListenerNotFoundException;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

public class Integration {

  public static void main(String[] args) throws IOException, ListenerNotFoundException, InstanceNotFoundException, MalformedObjectNameException {
    JMXServiceURL serviceURL = new JMXServiceURL("service:jmx:http://localhost:8080/jmx-http");
    try (JMXConnector connector = JMXConnectorFactory.newJMXConnector(serviceURL, null)) {
      connector.connect();
      NotificationListener listener = (notification, handback) -> {
        System.out.println("...");
      };
      connector.addConnectionNotificationListener(listener, null, null);
      MBeanServerConnection connection = connector.getMBeanServerConnection();
      System.out.println(connection.getMBeanCount());
      System.out.println(connection.getDefaultDomain());
      connector.removeConnectionNotificationListener(listener);
      ObjectName name = new ObjectName("name=JMImplementation:type=MBeanServerDelegate");
      connection.addNotificationListener(name, listener, null, null);
      System.in.read();
      connection.removeNotificationListener(name, listener);
    }

  }

}
