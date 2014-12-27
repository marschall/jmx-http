package com.github.marschall.jmxhttp.common.command;

import java.io.IOException;

import javax.management.JMException;
import javax.management.MBeanServerConnection;
import javax.management.NotificationFilter;
import javax.management.ObjectName;

public final class RemoveNotificationListenerRemote implements Command<Void> {

  private ObjectName name;
  private long listenerId;
  private NotificationFilter filter;
  private Object handback;
  private boolean hasArguments;

  public RemoveNotificationListenerRemote(ObjectName name, long listenerId) {
    this.name = name;
    this.listenerId = listenerId;

    this.hasArguments = false;
  }

  public RemoveNotificationListenerRemote(ObjectName name, long listenerId, NotificationFilter filter, Object handback) {
    this.name = name;
    this.listenerId = listenerId;
    this.filter = filter;
    this.handback = handback;

    this.hasArguments = true;
  }

  @Override
  public Void execute(MBeanServerConnection connection, NotificationRegistry notificationRegistry) throws JMException, IOException {
    if (this.hasArguments) {
      notificationRegistry.removeNotificationListener(name, listenerId, filter, handback);
    } else {
      notificationRegistry.removeNotificationListener(name, listenerId);
    }
    return null;
  }

}
