package com.github.marschall.jmxhttp.common.command;

import java.io.IOException;

import javax.management.JMException;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;

public class Invoke implements Command<Object> {

  private final ObjectName name;
  private final String operationName;
  private final Object[] params;
  private final String[] signature;

  public Invoke(ObjectName name, String operationName, Object[] params,
      String[] signature) {
    this.name = name;
    this.operationName = operationName;
    this.params = params;
    this.signature = signature;
  }

  @Override
  public Object execute(MBeanServerConnection connection) throws JMException, IOException {
    return connection.invoke(name, operationName, params, signature);
  }

}
