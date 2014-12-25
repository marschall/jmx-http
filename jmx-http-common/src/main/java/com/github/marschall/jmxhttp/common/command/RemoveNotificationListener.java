package com.github.marschall.jmxhttp.common.command;

import java.io.IOException;

import javax.management.JMException;
import javax.management.MBeanServerConnection;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;

public final class RemoveNotificationListener implements Command<Void> {

  private final ObjectName name;
  private final NotificationListener listener;
  private final ObjectName listenerName;
  private final NotificationFilter filter;
  private final Object handback;
  private final boolean hasArguments;

  public RemoveNotificationListener(ObjectName name, ObjectName listener) {
    this.name = name;
    this.listener = null;
    this.listenerName = listener;
    this.filter = null;
    this.handback = null;

    this.hasArguments = false;
  }

  public RemoveNotificationListener(ObjectName name, NotificationListener listener) {
    this.name = name;
    this.listener = listener;
    this.listenerName = null;
    this.filter = null;
    this.handback = null;

    this.hasArguments = false;
  }

  public RemoveNotificationListener(ObjectName name, ObjectName listener, NotificationFilter filter, Object handback) {
    this.name = name;
    this.listener = null;
    this.listenerName = listener;
    this.filter = filter;
    this.handback = handback;

    this.hasArguments = true;
  }

  public RemoveNotificationListener(ObjectName name, NotificationListener listener, NotificationFilter filter, Object handback) {
    this.name = name;
    this.listener = listener;
    this.listenerName = null;
    this.filter = filter;
    this.handback = handback;

    this.hasArguments = true;
  }

  @Override
  public Void execute(MBeanServerConnection connection) throws JMException, IOException {
    if (this.listener != null) {
      if (this.hasArguments) {
        connection.removeNotificationListener(name, listener, filter, this.handback);
      } else {
        connection.removeNotificationListener(name, listener);
      }
    } else {
      if (this.hasArguments) {
        connection.removeNotificationListener(name, listenerName, filter, this.handback);
      } else {
        connection.removeNotificationListener(name, listenerName);
      }
    }
    return null;
  }

}
