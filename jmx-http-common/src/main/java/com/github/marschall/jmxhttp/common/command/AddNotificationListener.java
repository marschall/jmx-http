package com.github.marschall.jmxhttp.common.command;

import java.io.IOException;

import javax.management.JMException;
import javax.management.MBeanServerConnection;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;

public final class AddNotificationListener implements Command<Void> {
  
  private final ObjectName name;
  private final NotificationListener listener;
  private final NotificationFilter filter;
  private final Object handback;
  private final ObjectName listenerName;

  public AddNotificationListener(ObjectName name, NotificationListener listener, NotificationFilter filter, Object handback) {
    this.name = name;
    this.listener = listener;
    this.listenerName = null;
    this.filter = filter;
    this.handback = handback;
  }

  public AddNotificationListener(ObjectName name, ObjectName listenerName, NotificationFilter filter, Object handback) {
    this.name = name;
    this.listener = null;
    this.listenerName = listenerName;
    this.filter = filter;
    this.handback = handback;
  }

  @Override
  public Void execute(MBeanServerConnection connection) throws JMException, IOException {
    if (this.listener != null) {
      connection.addNotificationListener(name, listener, filter, handback);
    } else {
      connection.addNotificationListener(name, listenerName, filter, handback);
    }
    return null;
  }

}
