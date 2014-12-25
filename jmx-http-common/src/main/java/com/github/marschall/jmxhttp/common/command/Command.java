package com.github.marschall.jmxhttp.common.command;

import java.io.IOException;
import java.io.Serializable;

import javax.management.JMException;
import javax.management.MBeanServerConnection;

/**
 * All commands are sent as serialized objects implementing this interface
 * from client to server (see the command pattern).
 *
 * @param <R> the result type
 */
public interface Command<R> extends Serializable {

  R execute(MBeanServerConnection connection) throws JMException, IOException;
  
}
