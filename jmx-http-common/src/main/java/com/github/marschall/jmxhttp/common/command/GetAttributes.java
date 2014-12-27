package com.github.marschall.jmxhttp.common.command;

import java.io.IOException;

import javax.management.AttributeList;
import javax.management.JMException;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;

public final class GetAttributes implements Command<AttributeList> {
  
  private final ObjectName name;
  private final String[] attributes;
  
  public GetAttributes(ObjectName name, String[] attributes) {
    this.name = name;
    this.attributes = attributes;
  }

  @Override
  public AttributeList execute(MBeanServerConnection connection, NotificationRegistry notificationRegistry) throws JMException, IOException {
    return connection.getAttributes(name, attributes);
  }

}
