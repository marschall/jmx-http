package com.github.marschall.jmxhttp.common.command;

import java.io.IOException;

import javax.management.JMException;
import javax.management.MBeanServerConnection;
import javax.management.NotificationFilter;
import javax.management.ObjectName;

public final class AddNotificationListenerRemote implements Command<Long> {
  
  private final ObjectName name;
  private final NotificationFilter filter;
  private final Object handback;

  public AddNotificationListenerRemote(ObjectName name, NotificationFilter filter, Object handback) {
    this.name = name;
    this.filter = filter;
    this.handback = handback;
  }



  @Override
  public Long execute(MBeanServerConnection connection, NotificationRegistry notificationRegistry) throws JMException, IOException {
    return notificationRegistry.addNotificationListener(this.name, this.filter, this.handback);
  }

}
