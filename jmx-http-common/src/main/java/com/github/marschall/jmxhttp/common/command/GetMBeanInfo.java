package com.github.marschall.jmxhttp.common.command;

import java.io.IOException;

import javax.management.JMException;
import javax.management.MBeanInfo;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;

public final class GetMBeanInfo implements Command<MBeanInfo> {

  private final ObjectName name;

  public GetMBeanInfo(ObjectName name) {
    this.name = name;
  }

  @Override
  public MBeanInfo execute(MBeanServerConnection connection) throws JMException, IOException {
    return connection.getMBeanInfo(name);
  }



}
