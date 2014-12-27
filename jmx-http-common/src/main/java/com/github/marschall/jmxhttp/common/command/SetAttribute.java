package com.github.marschall.jmxhttp.common.command;

import java.io.IOException;

import javax.management.Attribute;
import javax.management.JMException;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;

public final class SetAttribute implements Command<Void> {
  
  private final ObjectName name;
  private final Attribute attribute;
  
  public SetAttribute(ObjectName name, Attribute attribute) {
    this.name = name;
    this.attribute = attribute;
  }

  @Override
  public Void execute(MBeanServerConnection connection, NotificationRegistry notificationRegistry) throws JMException, IOException {
    connection.setAttribute(name, attribute);
    return null;
  }

}
