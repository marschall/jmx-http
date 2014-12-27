package com.github.marschall.jmxhttp.common.command;

import java.io.IOException;
import java.util.Set;

import javax.management.MBeanServerConnection;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.QueryExp;

public final class QueryMBeans implements Command<Set<ObjectInstance>> {
  
  private final ObjectName name;
  private final QueryExp query;
  
  public QueryMBeans(ObjectName name, QueryExp query) {
    this.name = name;
    this.query = query;
  }

  @Override
  public Set<ObjectInstance> execute(MBeanServerConnection connection, NotificationRegistry notificationRegistry) throws IOException {
    return connection.queryMBeans(name, query);
  }

}
