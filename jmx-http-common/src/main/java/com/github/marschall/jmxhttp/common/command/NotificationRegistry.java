package com.github.marschall.jmxhttp.common.command;

import java.io.IOException;

import javax.management.NotificationFilter;
import javax.management.ObjectName;

public interface NotificationRegistry {

  long addNotificationListener(ObjectName name, NotificationFilter filter, Object handback) throws IOException;
  
  void removeNotificationListener(ObjectName name, long listener) throws IOException;

  void removeNotificationListener(ObjectName name, long listener, NotificationFilter filter, Object handback) throws IOException;
  
}
