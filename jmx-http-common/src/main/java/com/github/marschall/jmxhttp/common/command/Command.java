package com.github.marschall.jmxhttp.common.command;

import java.io.IOException;

import javax.management.JMException;
import javax.management.MBeanServerConnection;

public interface Command<R> {

  R execute(MBeanServerConnection connection) throws JMException, IOException;
  
}
