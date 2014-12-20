package com.github.marschall.jmxhttp.common.command;

import java.io.IOException;

import javax.management.MBeanServerConnection;

public final class GetDomains implements Command<String[]> {

  @Override
  public String[] execute(MBeanServerConnection connection) throws IOException {
    return connection.getDomains();
  }

}
