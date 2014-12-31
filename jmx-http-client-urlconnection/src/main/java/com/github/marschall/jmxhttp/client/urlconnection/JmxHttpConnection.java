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
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
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
import com.github.marschall.jmxhttp.common.command.AddNotificationListenerRemote;
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
import com.github.marschall.jmxhttp.common.command.RemoveNotificationListenerRemote;
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

  private final Lock idLock;
  private final Map<Long, NotificationListener> listeners;
  private final Map<NotificationListener, Long> listenersToId;
  private long listenerIdGenerator;
  private final Map<Long, Object> handbacks;
  private final Map<Object, Long> handbacksToId;
  private long handbackIdGenerator;


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
    this.listeners = new HashMap<>();
    this.listenersToId = new IdentityHashMap<>();
    this.listenerIdGenerator = 0L;
    this.handbacks = new HashMap<>();
    this.handbacksToId = new IdentityHashMap<>();
    this.handbackIdGenerator = 0L;
    this.idLock = new ReentrantLock();
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
    long listenerId = this.registerListener(listener);
    Long handbackId = this.registerHandback(handback);
    send(new AddNotificationListenerRemote(name, listenerId, filter, handbackId));
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
    long listenerId = this.getListenerId(listener);
    send(new RemoveNotificationListenerRemote(name, listenerId));
  }

  @Override
  public void removeNotificationListener(ObjectName name, NotificationListener listener, NotificationFilter filter, Object handback) throws IOException, ListenerNotFoundException {
    long listenerId = this.getListenerId(listener);
    Long handbackId = this.getHandbackId(handback);
    send(new RemoveNotificationListenerRemote(name, listenerId, filter, handbackId));
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
    // REVIEW join?
    this.pollerThread.interrupt();
    // REVIEW unregister?
    this.listeners.clear();
    this.handbacks.clear();
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
        // TODO connection listeners?
        LOG.log(Level.WARNING, "could not open listen loop, notifications will be dropped", e);
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
    try {
      NotificationListener listener = getListener(listenerId);
      Object handback = getHandback(handbackId);
      listener.handleNotification(notification, handback);
    } catch (RuntimeException e) {
      LOG.log(Level.WARNING, "exception occrred while delivering event to listener", e);
    }
  }

  private NotificationListener getListener(long listenerId) {
    this.idLock.lock();
    try {
      NotificationListener listener = this.listeners.get(listenerId);
      if (listener == null) {
        System.out.println("no listener found for id: " + listenerId);
        throw new NoSuchElementException("no listener found for id: " + listenerId);
      }
      return listener;
    } finally {
      this.idLock.unlock();
    }
  }

  private long registerListener(NotificationListener listener) {
    this.idLock.lock();
    try {
      Objects.requireNonNull(listener, "listener must not be null");
      this.handbackIdGenerator += 1L;
      long id = this.listenerIdGenerator;
      NotificationListener previous = this.listeners.putIfAbsent(id, listener);
      if (previous != null) {
        System.out.println("listener " + listener + " already registered");
        throw new IllegalArgumentException("listener " + listener + " already registered");
      }
      this.listenersToId.put(listener, id);
      return id;
    } finally {
      this.idLock.unlock();
    }
  }

  private Long registerHandback(Object handback) {
    if (handback == null) {
      return null;
    }
    this.idLock.lock();
    try {
      this.handbackIdGenerator += 1L;
      long id = this.handbackIdGenerator;
      Object previous = this.handbacks.putIfAbsent(id, handback);
      if (previous != null) {
        System.out.println("handback " + handback + " already registered");
        throw new IllegalArgumentException("handback " + handback + " already registered");
      }
      handbacksToId.put(handback, id);
      return id;
    } finally {
      this.idLock.unlock();
    }
  }

  private Object getHandback(Long handbackId) {
    if (handbackId == null) {
      return null;
    }
    this.idLock.lock();
    try {
      Object handback = this.handbacks.get(handbackId);
      if (handback == null) {
        System.out.println("no handback found for id: " + handbackId);
        throw new NoSuchElementException("no handback found for id: " + handbackId);
      }
      return handback;
    } finally {
      this.idLock.unlock();
    }
  }

  private long getListenerId(NotificationListener listener) throws ListenerNotFoundException {
    this.idLock.lock();
    try {
      Long id = this.listenersToId.get(listener);
      if (id == null) {
        throw new ListenerNotFoundException("listener: " + listener + " not found");
      }
      return id;
    } finally {
      this.idLock.unlock();
    }
  }
  private Long getHandbackId(Object handback) throws ListenerNotFoundException {
    if (handback == null) {
      return null;
    }
    this.idLock.lock();
    try {
      Long id = this.handbacksToId.get(handback);
      if (id == null) {
        throw new ListenerNotFoundException("handback: " + handback + " not found");
      }
      return id;
    } finally {
      this.idLock.unlock();
    }
  }

}
