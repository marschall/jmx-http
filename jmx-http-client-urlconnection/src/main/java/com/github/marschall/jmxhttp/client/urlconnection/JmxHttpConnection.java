package com.github.marschall.jmxhttp.client.urlconnection;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.util.Optional;
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

import com.github.marschall.jmxhttp.common.command.ClassLoaderObjectInputStream;
import com.github.marschall.jmxhttp.common.command.Command;
import com.github.marschall.jmxhttp.common.command.CreateMBean;
import com.github.marschall.jmxhttp.common.command.GetAttribute;
import com.github.marschall.jmxhttp.common.command.GetAttributes;
import com.github.marschall.jmxhttp.common.command.GetDefaultDomain;
import com.github.marschall.jmxhttp.common.command.GetDomains;
import com.github.marschall.jmxhttp.common.command.GetMBeanCount;
import com.github.marschall.jmxhttp.common.command.GetMBeanInfo;
import com.github.marschall.jmxhttp.common.command.GetObjectInstance;
import com.github.marschall.jmxhttp.common.command.Invoke;
import com.github.marschall.jmxhttp.common.command.IsInstanceOf;
import com.github.marschall.jmxhttp.common.command.IsRegistered;
import com.github.marschall.jmxhttp.common.command.QueryMBeans;
import com.github.marschall.jmxhttp.common.command.QueryNames;
import com.github.marschall.jmxhttp.common.command.SetAttribute;
import com.github.marschall.jmxhttp.common.command.SetAttributes;
import com.github.marschall.jmxhttp.common.command.UnregisterMBean;

final class JmxHttpConnection implements MBeanServerConnection {
  
  private final HttpURLConnection urlConnection;
  private final Optional<String> credentials;
  private final ClassLoader classLoader;
  
  protected JmxHttpConnection(HttpURLConnection urlConnection, Optional<String> credentials) {
    this.urlConnection = urlConnection;
    this.credentials = credentials;
    this.classLoader = this.getClass().getClassLoader();
  }

  @Override
  public ObjectInstance createMBean(String className, ObjectName name) throws ReflectionException, InstanceAlreadyExistsException, MBeanRegistrationException, MBeanException, NotCompliantMBeanException, IOException {
    return send(new CreateMBean(className, name, null, null, null));
  }

  @Override
  public ObjectInstance createMBean(String className, ObjectName name, ObjectName loaderName) throws ReflectionException, InstanceAlreadyExistsException, MBeanRegistrationException, MBeanException, NotCompliantMBeanException, InstanceNotFoundException, IOException {
    return send(new CreateMBean(className, name, loaderName, null, null));
  }

  @Override
  public ObjectInstance createMBean(String className, ObjectName name, Object[] params, String[] signature) throws ReflectionException, InstanceAlreadyExistsException, MBeanRegistrationException, MBeanException, NotCompliantMBeanException, IOException {
    return send(new CreateMBean(className, name, null, params, signature));
  }

  @Override
  public ObjectInstance createMBean(String className, ObjectName name, ObjectName loaderName, Object[] params, String[] signature) throws ReflectionException, InstanceAlreadyExistsException, MBeanRegistrationException, MBeanException, NotCompliantMBeanException, InstanceNotFoundException, IOException {
    return send(new CreateMBean(className, name, loaderName, params, signature));
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
    return send(new GetAttribute(name, attribute));
  }

  @Override
  public AttributeList getAttributes(ObjectName name, String[] attributes) throws InstanceNotFoundException, ReflectionException, IOException {
    return send(new GetAttributes(name, attributes));
  }

  @Override
  public void setAttribute(ObjectName name, Attribute attribute) throws InstanceNotFoundException, AttributeNotFoundException, InvalidAttributeValueException, MBeanException, ReflectionException, IOException {
    send(new SetAttribute(name, attribute));
  }

  @Override
  public AttributeList setAttributes(ObjectName name, AttributeList attributes) throws InstanceNotFoundException, ReflectionException, IOException {
    return send(new SetAttributes(name, attributes));
  }

  @Override
  public Object invoke(ObjectName name, String operationName, Object[] params, String[] signature) throws InstanceNotFoundException, MBeanException, ReflectionException, IOException {
    return send(new Invoke(name, operationName, params, signature));
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
  
  private synchronized <R> R send(Command<R> command) throws IOException {
    urlConnection.setDoOutput(true);
    urlConnection.setChunkedStreamingMode(0);
    urlConnection.setRequestMethod("POST");
    if (credentials.isPresent()) {
      urlConnection.setRequestProperty ("Authorization", credentials.get());
    }
//    urlConnection.setRequestProperty("Connection", "keep-alive");
    try (OutputStream out = urlConnection.getOutputStream();
        ObjectOutputStream stream = new ObjectOutputStream(new BufferedOutputStream(out))) {
      stream.writeObject(command);
    }
    
    int status = urlConnection.getResponseCode();
    if (status == 100) {
      try (InputStream in = urlConnection.getInputStream();
          ObjectInputStream stream = new ClassLoaderObjectInputStream(new BufferedInputStream(in), classLoader)) {
        Object result;
        try {
          result = stream.readObject();
        } catch (ClassNotFoundException e) {
          throw new IOException("class not found", e);
        }
        if (result instanceof Exception) {
          throw new IOException("exception occurred on server", (Exception) result);
//          throw (Exception) result;
        } else {
          return (R) result;
        }
      }
    } else {

    }
    return null;
  }

  void close() {
    this.urlConnection.disconnect();
  }

}
