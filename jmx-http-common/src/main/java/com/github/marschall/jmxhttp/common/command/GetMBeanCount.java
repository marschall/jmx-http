package com.github.marschall.jmxhttp.common.command;

import java.io.IOException;

import javax.management.MBeanServerConnection;

public final class GetMBeanCount implements Command<Integer> {

  @Override
  public Integer execute(MBeanServerConnection connection, NotificationRegistry notificationRegistry) throws IOException {
    return connection.getMBeanCount();
  }

}
