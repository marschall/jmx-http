package com.github.marschall.jmxhttp.client.urlconnection;

import java.io.IOException;
import java.net.URL;
import java.util.Map;

import javax.management.ListenerNotFoundException;
import javax.management.MBeanServerConnection;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.remote.JMXConnector;
import javax.security.auth.Subject;

final class JmxHttpConnector implements JMXConnector {

  JmxHttpConnector(URL url) {
    // TODO Auto-generated constructor stub
  }

  @Override
  public void connect() throws IOException {
    // TODO Auto-generated method stub

  }

  @Override
  public void connect(Map<String, ?> env) throws IOException {
    // TODO Auto-generated method stub

  }

  @Override
  public MBeanServerConnection getMBeanServerConnection() throws IOException {
    // TODO Auto-generated method stub
    return new JmxHttpConnection();
  }

  @Override
  public MBeanServerConnection getMBeanServerConnection(Subject delegationSubject) throws IOException {
    // TODO Auto-generated method stub
    return new JmxHttpConnection();
  }

  @Override
  public void close() throws IOException {
    // TODO Auto-generated method stub

  }

  @Override
  public void addConnectionNotificationListener(NotificationListener listener,
      NotificationFilter filter, Object handback) {
    // TODO Auto-generated method stub

  }

  @Override
  public void removeConnectionNotificationListener(NotificationListener listener)
      throws ListenerNotFoundException {
    // TODO Auto-generated method stub

  }

  @Override
  public void removeConnectionNotificationListener(NotificationListener l,
      NotificationFilter f, Object handback) throws ListenerNotFoundException {
    // TODO Auto-generated method stub

  }

  @Override
  public String getConnectionId() throws IOException {
    // TODO Auto-generated method stub
    return null;
  }

}
