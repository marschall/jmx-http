package com.github.marschall.jmxhttp.common.command;

import java.io.IOException;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.QueryExp;

public final class QueryMBeans implements Command {
  
  private final ObjectName name;
  private final QueryExp query;
  
  public QueryMBeans(ObjectName name, QueryExp query) {
    this.name = name;
    this.query = query;
  }

  @Override
  public Object execute(MBeanServerConnection connection) throws IOException {
    return connection.queryMBeans(name, query);
  }

}
