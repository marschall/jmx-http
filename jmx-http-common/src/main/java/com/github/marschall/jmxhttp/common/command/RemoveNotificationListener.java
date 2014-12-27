package com.github.marschall.jmxhttp.common.command;

import java.io.IOException;

import javax.management.JMException;
import javax.management.MBeanServerConnection;
import javax.management.NotificationFilter;
import javax.management.ObjectName;

public final class RemoveNotificationListener implements Command<Void> {

  private final ObjectName name;
  private final ObjectName listenerName;
  private final NotificationFilter filter;
  private final Object handback;
  private final boolean hasArguments;

  public RemoveNotificationListener(ObjectName name, ObjectName listener) {
    this.name = name;
    this.listenerName = listener;
    this.filter = null;
    this.handback = null;

    this.hasArguments = false;
  }

  public RemoveNotificationListener(ObjectName name, ObjectName listener, NotificationFilter filter, Object handback) {
    this.name = name;
    this.listenerName = listener;
    this.filter = filter;
    this.handback = handback;

    this.hasArguments = true;
  }

  @Override
  public Void execute(MBeanServerConnection connection, NotificationRegistry notificationRegistry) throws JMException, IOException {
    if (this.hasArguments) {
      connection.removeNotificationListener(this.name, this.listenerName, this.filter, this.handback);
    } else {
      connection.removeNotificationListener(this.name, this.listenerName);
    }
    return null;
  }

}
