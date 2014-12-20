package com.github.marschall.jmxhttp.common.command;

import java.io.IOException;

import javax.management.JMException;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;

public final class GetAttribute implements Command<Object> {
  
  private final ObjectName name;
  private final String attribute;

  public GetAttribute(ObjectName name, String attribute) {
    this.name = name;
    this.attribute = attribute;
  }

  @Override
  public Object execute(MBeanServerConnection connection) throws JMException, IOException {
    return connection.getAttribute(name, attribute);
  }

}
