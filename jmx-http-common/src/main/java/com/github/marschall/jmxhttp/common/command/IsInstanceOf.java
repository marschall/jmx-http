package com.github.marschall.jmxhttp.common.command;

import java.io.IOException;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;

public final class IsInstanceOf implements Command<Boolean> {
  
  private final ObjectName name;
  private final String className;

  public IsInstanceOf(ObjectName name, String className) {
    this.name = name;
    this.className = className;
  }

  @Override
  public Boolean execute(MBeanServerConnection connection) throws InstanceNotFoundException, IOException {
    return connection.isInstanceOf(name, className);
  }
  
  

}
