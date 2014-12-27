package com.github.marschall.jmxhttp.common.command;

import java.io.IOException;

import javax.management.JMException;
import javax.management.MBeanServerConnection;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;

public final class AddNotificationListener implements Command<Void> {
  
  private final ObjectName name;
  private final NotificationFilter filter;
  private final Object handback;
  private final ObjectName listenerName;

  public AddNotificationListener(ObjectName name, ObjectName listenerName, NotificationFilter filter, Object handback) {
    this.name = name;
    this.listenerName = listenerName;
    this.filter = filter;
    this.handback = handback;
  }

  @Override
  public Void execute(MBeanServerConnection connection) throws JMException, IOException {
    connection.addNotificationListener(name, listenerName, filter, handback);
    return null;
  }

}
