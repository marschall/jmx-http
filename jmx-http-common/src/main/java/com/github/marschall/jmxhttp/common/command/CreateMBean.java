package com.github.marschall.jmxhttp.common.command;

import java.io.IOException;

import javax.management.JMException;
import javax.management.MBeanServerConnection;
import javax.management.ObjectInstance;
import javax.management.ObjectName;

public final class CreateMBean implements Command<ObjectInstance> {

  private final String className;
  private final ObjectName name;
  private final ObjectName loaderName;
  private final Object[] params;
  private final String[] signature;
  
  public CreateMBean(String className, ObjectName name, ObjectName loaderName, Object[] params, String[] signature) {
    this.className = className;
    this.name = name;
    this.loaderName = loaderName;
    this.params = params;
    this.signature = signature;
  }

  @Override
  public ObjectInstance execute(MBeanServerConnection connection, NotificationRegistry notificationRegistry) throws JMException, IOException {
    return connection.createMBean(className, name, loaderName, params, signature);
  }

}
