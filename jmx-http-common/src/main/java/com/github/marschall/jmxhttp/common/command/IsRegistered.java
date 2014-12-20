package com.github.marschall.jmxhttp.common.command;

import java.io.IOException;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;

public final class IsRegistered implements Command<Boolean> {
  
  private final ObjectName name;

  public IsRegistered(ObjectName name) {
    this.name = name;
  }

  @Override
  public Boolean execute(MBeanServerConnection connection) throws IOException {
    return connection.isRegistered(name);
  }
  
  

}
