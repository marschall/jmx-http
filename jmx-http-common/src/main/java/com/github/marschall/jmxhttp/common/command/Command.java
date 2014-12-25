package com.github.marschall.jmxhttp.common.command;

import java.io.IOException;
import java.io.Serializable;

import javax.management.JMException;
import javax.management.MBeanServerConnection;

public interface Command<R> extends Serializable {

  R execute(MBeanServerConnection connection) throws JMException, IOException;
  
}
