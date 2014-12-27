package com.github.marschall.jmxhttp.common.command;

import java.io.IOException;
import java.util.Set;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.QueryExp;

public final class QueryNames implements Command<Set<ObjectName>> {

  private final ObjectName name;
  private final QueryExp query;

  public QueryNames(ObjectName name, QueryExp query) {
    this.name = name;
    this.query = query;
  }

  @Override
  public Set<ObjectName> execute(MBeanServerConnection connection, NotificationRegistry notificationRegistry) throws IOException {
    return connection.queryNames(name, query);
  }

}
