package com.github.marschall.jmxhttp.common.command;

import java.io.IOException;

import javax.management.JMException;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;

public final class UnregisterMBean implements Command<Void> {
  
  private final ObjectName name;

  public UnregisterMBean(ObjectName name) {
    this.name = name;
  }

  @Override
  public Void execute(MBeanServerConnection connection, NotificationRegistry notificationRegistry) throws JMException, IOException {
    connection.unregisterMBean(name);
    return null;
  }

}
