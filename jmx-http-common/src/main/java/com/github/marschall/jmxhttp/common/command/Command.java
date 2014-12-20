package com.github.marschall.jmxhttp.common.command;

import java.io.IOException;

import javax.management.MBeanServerConnection;

public interface Command {

  Object execute(MBeanServerConnection connection) throws IOException;
  
}
