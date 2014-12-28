package com.github.marschall.jmxhttp.common.command;

import java.io.IOException;

import javax.management.JMException;
import javax.management.MBeanServerConnection;
import javax.management.NotificationFilter;
import javax.management.ObjectName;

public final class RemoveNotificationListenerRemote implements Command<Void> {

  private final ObjectName name;
  private final long listenerId;
  private final NotificationFilter filter;
  private final Long handbackId;
  private final boolean hasArguments;

  public RemoveNotificationListenerRemote(ObjectName name, long listenerId) {
    this.name = name;
    this.listenerId = listenerId;
    this.filter = null;
    this.handbackId = null;

    this.hasArguments = false;
  }

  public RemoveNotificationListenerRemote(ObjectName name, long listenerId, NotificationFilter filter, Long handbackId) {
    this.name = name;
    this.listenerId = listenerId;
    this.filter = filter;
    this.handbackId = handbackId;

    this.hasArguments = true;
  }

  @Override
  public Void execute(MBeanServerConnection connection, NotificationRegistry notificationRegistry) throws JMException, IOException {
    if (this.hasArguments) {
      notificationRegistry.removeNotificationListener(name, listenerId, filter, handbackId);
    } else {
      notificationRegistry.removeNotificationListener(name, listenerId);
    }
    return null;
  }

}
