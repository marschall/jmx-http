package com.github.marschall.jmxhttp.common.command;

import java.io.IOException;

import javax.management.NotificationFilter;
import javax.management.ObjectName;

public interface NotificationRegistry {

  long addNotificationListener(ObjectName name, NotificationFilter filter, long listenerId, Long handbackId) throws IOException;
  
  void removeNotificationListener(ObjectName name, long listenerId) throws IOException;

  void removeNotificationListener(ObjectName name, long listenerId, NotificationFilter filter, Long objectId) throws IOException;
  
}
