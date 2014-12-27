package com.github.marschall.jmxhttp.common.command;

import java.io.IOException;

import javax.management.JMException;
import javax.management.MBeanServerConnection;
import javax.management.ObjectInstance;
import javax.management.ObjectName;

public final class GetObjectInstance implements Command<ObjectInstance> {
  
  private final ObjectName name;

  public GetObjectInstance(ObjectName name) {
    this.name = name;
  }

  @Override
  public ObjectInstance execute(MBeanServerConnection connection, NotificationRegistry notificationRegistry) throws JMException, IOException {
    return connection.getObjectInstance(name);
  }

}
