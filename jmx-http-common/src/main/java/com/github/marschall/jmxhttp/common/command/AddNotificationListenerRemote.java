package com.github.marschall.jmxhttp.common.command;

import java.io.IOException;

import javax.management.JMException;
import javax.management.MBeanServerConnection;
import javax.management.NotificationFilter;
import javax.management.ObjectName;

public final class AddNotificationListenerRemote implements Command<Void> {

  private final ObjectName name;
  private final long listenerId;
  private final NotificationFilter filter;
  private final Long handbackId;

  public AddNotificationListenerRemote(ObjectName name, long listenerId, NotificationFilter filter, Long handbackId) {
    this.name = name;
    this.listenerId = listenerId;
    this.filter = filter;
    this.handbackId = handbackId;
  }

  @Override
  public Void execute(MBeanServerConnection connection, NotificationRegistry notificationRegistry) throws JMException, IOException {
    notificationRegistry.addNotificationListener(this.name, this.listenerId, this.filter, this.handbackId);
    return null;
  }

}
