package com.github.marschall.jmxhttp.client.urlconnection;

import java.io.IOException;
import java.util.Set;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.InvalidAttributeValueException;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServerConnection;
import javax.management.NotCompliantMBeanException;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.QueryExp;
import javax.management.ReflectionException;

import com.github.marschall.jmxhttp.common.command.Command;
import com.github.marschall.jmxhttp.common.command.GetDefaultDomain;
import com.github.marschall.jmxhttp.common.command.GetDomains;
import com.github.marschall.jmxhttp.common.command.GetMBeanCount;
import com.github.marschall.jmxhttp.common.command.GetMBeanInfo;
import com.github.marschall.jmxhttp.common.command.GetObjectInstance;
import com.github.marschall.jmxhttp.common.command.IsInstanceOf;
import com.github.marschall.jmxhttp.common.command.IsRegistered;
import com.github.marschall.jmxhttp.common.command.QueryMBeans;
import com.github.marschall.jmxhttp.common.command.QueryNames;
import com.github.marschall.jmxhttp.common.command.UnregisterMBean;

public class JmxHttpConnection implements MBeanServerConnection {

  @Override
  public ObjectInstance createMBean(String className, ObjectName name)
      throws ReflectionException, InstanceAlreadyExistsException,
      MBeanRegistrationException, MBeanException, NotCompliantMBeanException,
      IOException {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public ObjectInstance createMBean(String className, ObjectName name,
      ObjectName loaderName) throws ReflectionException,
      InstanceAlreadyExistsException, MBeanRegistrationException,
      MBeanException, NotCompliantMBeanException, InstanceNotFoundException,
      IOException {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public ObjectInstance createMBean(String className, ObjectName name,
      Object[] params, String[] signature) throws ReflectionException,
      InstanceAlreadyExistsException, MBeanRegistrationException,
      MBeanException, NotCompliantMBeanException, IOException {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public ObjectInstance createMBean(String className, ObjectName name,
      ObjectName loaderName, Object[] params, String[] signature)
      throws ReflectionException, InstanceAlreadyExistsException,
      MBeanRegistrationException, MBeanException, NotCompliantMBeanException,
      InstanceNotFoundException, IOException {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public void unregisterMBean(ObjectName name) throws InstanceNotFoundException, MBeanRegistrationException, IOException {
    send(new UnregisterMBean(name));
  }

  @Override
  public ObjectInstance getObjectInstance(ObjectName name) throws InstanceNotFoundException, IOException {
    return send(new GetObjectInstance(name));
  }

  @Override
  public Set<ObjectInstance> queryMBeans(ObjectName name, QueryExp query) throws IOException {
    return send(new QueryMBeans(name, query));
  }

  @Override
  public Set<ObjectName> queryNames(ObjectName name, QueryExp query) throws IOException {
    return send(new QueryNames(name, query));
  }

  @Override
  public boolean isRegistered(ObjectName name) throws IOException {
    return send(new IsRegistered(name));
  }

  @Override
  public Integer getMBeanCount() throws IOException {
    return send(new GetMBeanCount());
  }

  @Override
  public Object getAttribute(ObjectName name, String attribute) throws MBeanException, AttributeNotFoundException, InstanceNotFoundException, ReflectionException, IOException {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public AttributeList getAttributes(ObjectName name, String[] attributes)
      throws InstanceNotFoundException, ReflectionException, IOException {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public void setAttribute(ObjectName name, Attribute attribute)
      throws InstanceNotFoundException, AttributeNotFoundException,
      InvalidAttributeValueException, MBeanException, ReflectionException,
      IOException {
    // TODO Auto-generated method stub
    
  }

  @Override
  public AttributeList setAttributes(ObjectName name, AttributeList attributes)
      throws InstanceNotFoundException, ReflectionException, IOException {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Object invoke(ObjectName name, String operationName, Object[] params,
      String[] signature) throws InstanceNotFoundException, MBeanException,
      ReflectionException, IOException {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public String getDefaultDomain() throws IOException {
    return send(new GetDefaultDomain());
  }

  @Override
  public String[] getDomains() throws IOException {
    return send(new GetDomains());
  }

  @Override
  public void addNotificationListener(ObjectName name, NotificationListener listener, NotificationFilter filter, Object handback) {
    throw new UnsupportedOperationException("notification listeners aren't supported");
  }

  @Override
  public void addNotificationListener(ObjectName name, ObjectName listener, NotificationFilter filter, Object handback) {
    throw new UnsupportedOperationException("notification listeners aren't supported");
  }

  @Override
  public void removeNotificationListener(ObjectName name, ObjectName listener) {
    throw new UnsupportedOperationException("notification listeners aren't supported");
  }

  @Override
  public void removeNotificationListener(ObjectName name, ObjectName listener, NotificationFilter filter, Object handback) {
    throw new UnsupportedOperationException("notification listeners aren't supported");
  }

  @Override
  public void removeNotificationListener(ObjectName name, NotificationListener listener) {
    throw new UnsupportedOperationException("notification listeners aren't supported");
  }

  @Override
  public void removeNotificationListener(ObjectName name, NotificationListener listener, NotificationFilter filter, Object handback) {
    throw new UnsupportedOperationException("notification listeners aren't supported");
  }

  @Override
  public MBeanInfo getMBeanInfo(ObjectName name) throws InstanceNotFoundException, IntrospectionException, ReflectionException, IOException {
    return send(new GetMBeanInfo(name));
  }

  @Override
  public boolean isInstanceOf(ObjectName name, String className) throws InstanceNotFoundException, IOException {
    return send(new IsInstanceOf(name, className));
  }
  
  private <R> R send(Command<R> command) {
    // TODO Auto-generated method stub
    // TODO check if result is an exception
    return null;
  }

}
