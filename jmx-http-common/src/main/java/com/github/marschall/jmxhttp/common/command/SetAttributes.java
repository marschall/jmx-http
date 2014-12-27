package com.github.marschall.jmxhttp.common.command;

import java.io.IOException;

import javax.management.AttributeList;
import javax.management.JMException;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;

public final class SetAttributes implements Command<AttributeList> {
  
  private final ObjectName name;
  private final AttributeList attributes;
  
  public SetAttributes(ObjectName name, AttributeList attributes) {
    this.name = name;
    this.attributes = attributes;
  }


  @Override
  public AttributeList execute(MBeanServerConnection connection, NotificationRegistry notificationRegistry) throws JMException, IOException {
    return connection.setAttributes(name, attributes);
  }

}
