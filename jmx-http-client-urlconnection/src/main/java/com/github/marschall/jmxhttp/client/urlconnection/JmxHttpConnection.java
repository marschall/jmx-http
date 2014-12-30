package com.github.marschall.jmxhttp.client.urlconnection;

import static com.github.marschall.jmxhttp.client.urlconnection.UrlConnectionUtil.readResponseAsObject;
import static com.github.marschall.jmxhttp.common.http.HttpConstant.ACTION_LISTEN;
import static com.github.marschall.jmxhttp.common.http.HttpConstant.JAVA_SERIALIZED_OBJECT;
import static com.github.marschall.jmxhttp.common.http.HttpConstant.PARAMETER_ACTION;
import static com.github.marschall.jmxhttp.common.http.HttpConstant.PARAMETER_CORRELATION_ID;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.lang.invoke.MethodHandles;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.InvalidAttributeValueException;
import javax.management.ListenerNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServerConnection;
import javax.management.NotCompliantMBeanException;
import javax.management.Notification;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.QueryExp;
import javax.management.ReflectionException;

import com.github.marschall.jmxhttp.common.command.AddNotificationListener;
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
import com.github.marschall.jmxhttp.common.command.RemoveNotificationListener;
import com.github.marschall.jmxhttp.common.command.SetAttribute;
import com.github.marschall.jmxhttp.common.command.SetAttributes;
import com.github.marschall.jmxhttp.common.command.UnregisterMBean;
import com.github.marschall.jmxhttp.common.http.Registration;
import com.github.marschall.jmxhttp.common.http.RemoteNotification;


/**
 * The actual client to server connection happens where, delegates to
 * {@link HttpURLConnection}.
 */
final class JmxHttpConnection implements MBeanServerConnection {

  private static final int FUDGE = (int) TimeUnit.SECONDS.toMillis(1L);

  private static final Logger LOG = Logger.getLogger(MethodHandles.lookup().lookupClass().getName());
  
  private final Registration registration;
  private final URL url;
  private final URL actionUrl;
  private final URL listenUrl;
  private final Optional<String> credentials;
  private final ClassLoader classLoader;
  private final Notifier notifier;
  private final Thread pollerThread;

  protected JmxHttpConnection(int id, Registration registration, URL url, Optional<String> credentials, Notifier notifier) throws MalformedURLException {
    this.registration = registration;
    this.url = url;
    this.actionUrl = new URL(this.url.toString() + '?' + PARAMETER_CORRELATION_ID + '=' + registration.getCorrelationId());
    this.listenUrl = new URL(this.url.toString() + '?' + PARAMETER_CORRELATION_ID + '=' + registration.getCorrelationId() + '&' + PARAMETER_ACTION + '=' + ACTION_LISTEN);
    this.credentials = credentials;
    this.classLoader = JmxHttpConnection.class.getClassLoader();
    this.notifier = notifier;
    this.pollerThread = new Thread(this::listenLoop, "Long-Poll-Client for " + id);
    this.pollerThread.start();
  }

  Optional<String> getCredentials() {
    return credentials;
  }

  long getCorrelationId() {
    return registration.getCorrelationId();
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
  public void addNotificationListener(ObjectName name, NotificationListener listener, NotificationFilter filter, Object handback) throws IOException {
    System.out.println("addNotificationListener(name=" + name + ", handback=" + handback + ")");
    System.out.println("addNotificationListener(name=" + name.getCanonicalName() + ", handback=" + handback + ")");
    //    long listenerId = send(new AddNotificationListenerRemote(name, filter, handback));
    //    mapListener(listener, listenerId, handback);
  }

  @Override
  public void addNotificationListener(ObjectName name, ObjectName listener, NotificationFilter filter, Object handback) throws IOException {
    send(new AddNotificationListener(name, listener, filter, handback));
  }

  @Override
  public void removeNotificationListener(ObjectName name, ObjectName listener) throws IOException {
    send(new RemoveNotificationListener(name, listener));
  }

  @Override
  public void removeNotificationListener(ObjectName name, ObjectName listener, NotificationFilter filter, Object handback) throws IOException {
    send(new RemoveNotificationListener(name, listener, filter, handback));
  }

  @Override
  public void removeNotificationListener(ObjectName name, NotificationListener listener) throws IOException, ListenerNotFoundException {
    //    long listenerId = getListenerId(listener);
    //    send(new RemoveNotificationListenerRemote(name, listenerId));
  }

  @Override
  public void removeNotificationListener(ObjectName name, NotificationListener listener, NotificationFilter filter, Object handback) throws IOException, ListenerNotFoundException {
    //    long listenerId = getListenerId(listener);
    //    send(new RemoveNotificationListenerRemote(name, listenerId, filter, handback));
  }

  @Override
  public MBeanInfo getMBeanInfo(ObjectName name) throws InstanceNotFoundException, IntrospectionException, ReflectionException, IOException {
    return send(new GetMBeanInfo(name));
  }

  @Override
  public boolean isInstanceOf(ObjectName name, String className) throws InstanceNotFoundException, IOException {
    return send(new IsInstanceOf(name, className));
  }
  
  void close() {
    this.pollerThread.interrupt();
    // REVIEW join?
  }

  private <R> R sendProtected(Command<R> command) throws IOException {
    HttpURLConnection urlConnection = this.openConnection();
    try {
      try (OutputStream out = urlConnection.getOutputStream();
          ObjectOutputStream stream = new ObjectOutputStream(out)) {
        stream.writeObject(command);
      }

      return (R) readResponseAsObject(urlConnection, this.classLoader);
    } finally {
      urlConnection.disconnect();
    }
  }

  private synchronized <R> R send(Command<R> command) throws IOException {
    try {
      return this.sendProtected(command);
    } catch (IOException e) {
      this.notifier.exceptionOccurred(e);
      throw e;
    }
  }

  private HttpURLConnection openConnection() throws IOException {
    // can only be set once
    HttpURLConnection urlConnection = (HttpURLConnection) this.actionUrl.openConnection();
    urlConnection.setDoOutput(true);
    urlConnection.setChunkedStreamingMode(0);
    urlConnection.setRequestMethod("POST");
    if (credentials.isPresent()) {
      urlConnection.setRequestProperty("Authorization", credentials.get());
    }
    urlConnection.setRequestProperty("Content-type", JAVA_SERIALIZED_OBJECT);
    return urlConnection;
  }
  
  private HttpURLConnection openListenConnection() throws IOException {
    HttpURLConnection urlConnection = (HttpURLConnection) this.listenUrl.openConnection();
    if (credentials.isPresent()) {
      urlConnection.setRequestProperty("Authorization", credentials.get());
    }
    int timeout = Math.max(0, (int) this.registration.getTimeoutMilliseconds() + FUDGE);
    urlConnection.setReadTimeout(timeout);
    return urlConnection;
  }

  private void listenLoop() {
    while (!Thread.currentThread().isInterrupted()) {
      HttpURLConnection urlConnection;
      try {
        urlConnection = this.openListenConnection();
      } catch (IOException e) {
        LOG.log(Level.WARNING, "could not open listen loop, notifications will be dropped", e);
        // TODO connection listeners?
        return;
      }
      try {
        Object response;
        try {
          response = readResponseAsObject(urlConnection, classLoader);
        } catch (SocketTimeoutException e) {
          LOG.log(Level.FINE, "long poll read timeout", e);
          continue;
        } catch (IOException e) {
          // TODO connection listeners?
          LOG.log(Level.WARNING, "could not read response", e);
          continue;
        }
        if (response instanceof List) {
          List<?> notifications = (List<?>) response;
          for (Object each : notifications) {
            if (each instanceof RemoteNotification) {
              RemoteNotification notification = (RemoteNotification) each;
              sendNotification(notification.getNotification(), notification.getListenerId(), notification.getObjectId());
            } else {
              if (each != null) {
                LOG.log(Level.WARNING, "notifaction ignored, has to be " + RemoteNotification.class + " but was " + each.getClass());
              } else {
                LOG.log(Level.WARNING, "notifaction ignored, has to be " + RemoteNotification.class + " but was null");
              }
            }
          }
        } else {
          if (response != null) {
            LOG.log(Level.WARNING, "response ignored, has to be " + List.class + " but was " + response.getClass());
          } else {
            LOG.log(Level.WARNING, "response ignored, has to be " + List.class + " but was null");
          }
        }
      } finally {
        urlConnection.disconnect();
      }
    }
  }

  private void sendNotification(Notification notification, long listenerId, Long handbackId) {

  }
  //
  //  private Long registerHandback(Object handback) {
  //    if (handback == null) {
  //      return null;
  //    }
  //  }
  //
  //  private Object getHandback(Long handbackId) {
  //    if (handbackId == null) {
  //      return null;
  //    }
  //  }
  //
  //  private void mapListener(NotificationListener listener, long listenerId, Object handback) {
  //    // TODO Auto-generated method stub
  //
  //  }
  //  private long getListenerId(NotificationListener listener) throws ListenerNotFoundException {
  //  }

}
