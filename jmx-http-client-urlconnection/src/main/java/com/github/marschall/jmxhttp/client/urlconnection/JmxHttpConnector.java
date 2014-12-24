package com.github.marschall.jmxhttp.client.urlconnection;

import static java.nio.charset.StandardCharsets.US_ASCII;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.Principal;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.management.ListenerNotFoundException;
import javax.management.MBeanServerConnection;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXPrincipal;
import javax.security.auth.Subject;

final class JmxHttpConnector implements JMXConnector {
  
  enum State {
    INITIAL,
    CONNECTED,
    CLOSED;
  }
  
  private static final AtomicInteger ID_GENERATOR = new AtomicInteger(1);

  private final URL url;
  
  private final int id;
  
  private final Lock sateLock;
  
  private State state;

  // TODO Auto-generated method stub
  private final List<Subscription> connectionNotificationListeners;

  private JmxHttpConnection mBeanServerConnection;

  JmxHttpConnector(URL url) {
    this.url = url;
    this.connectionNotificationListeners = new CopyOnWriteArrayList<>();
    this.id = ID_GENERATOR.incrementAndGet();
    this.sateLock = new ReentrantLock();
    this.state = State.INITIAL;
    
  }

  @Override
  public void connect() throws IOException {
    this.connect(null);

  }

  @Override
  public void connect(Map<String, ?> env) throws IOException {
    this.sateLock.lock();
    try {
      if (this.state == State.CONNECTED) {
        return;
      }
      if (this.state == State.CLOSED) {
        throw new IOException("already closed");
      }
      
      this.state = State.CONNECTED;
      Optional<String> credentials = extractCredentials(env);
      this.mBeanServerConnection = new JmxHttpConnection(
          (HttpURLConnection) this.url.openConnection(), credentials);
    } finally {
      this.sateLock.unlock();
    }

  }

  private static Optional<String> extractCredentials(Map<String, ?> env) {
    if (env == null) {
      return Optional.empty();
    }
    Object possibleCredentials = env.get(CREDENTIALS);
    if (possibleCredentials instanceof String[]) {
      String[] credentialArray = (String[]) possibleCredentials;
      String username = credentialArray[0];
      String password = credentialArray[1];
      String userpass = username + ":" + password;
      String basicAuth = "Basic " + new String(Base64.getEncoder().encode(userpass.getBytes(US_ASCII)), US_ASCII);
      return Optional.of(basicAuth);
    } else {
      return Optional.empty();
    }
  }

  @Override
  public MBeanServerConnection getMBeanServerConnection() throws IOException {
    return this.mBeanServerConnection;
  }

  @Override
  public MBeanServerConnection getMBeanServerConnection(Subject delegationSubject) throws IOException {
    throw new UnsupportedOperationException("delegation");
  }

  @Override
  public void close() throws IOException {
    this.sateLock.lock();
    try {
      if (this.state == State.CLOSED) {
        return;
      }
      this.state = State.CLOSED;
      if (this.mBeanServerConnection != null) {
        this.mBeanServerConnection.close();
      }
    } finally {
      this.sateLock.unlock();
    }

  }

  @Override
  public void addConnectionNotificationListener(NotificationListener listener, NotificationFilter filter, Object handback) {
    this.connectionNotificationListeners.add(new Subscription(listener, filter, handback));
  }

  @Override
  public void removeConnectionNotificationListener(NotificationListener listener)
      throws ListenerNotFoundException {

    Iterator<Subscription> iterator = this.connectionNotificationListeners.iterator();
    while (iterator.hasNext()) {
      Subscription subscription = iterator.next();
      if (subscription.listener == listener) {
        iterator.remove();
      }
    }

  }

  @Override
  public void removeConnectionNotificationListener(NotificationListener listener, NotificationFilter filter, Object handback) throws ListenerNotFoundException {
    this.connectionNotificationListeners.remove(new Subscription(listener, filter, handback));
  }

  @Override
  public String getConnectionId() throws IOException {
    AccessControlContext acc = AccessController.getContext();
    Subject subject = Subject.getSubject(acc);
    if (subject != null) {
   // Retrieve JMXPrincipal from Subject
      Set<JMXPrincipal> principals = subject.getPrincipals(JMXPrincipal.class);
      if (principals == null || principals.isEmpty()) {
          throw new SecurityException("Access denied");
      }
      Principal principal = principals.iterator().next();
      String identity = principal.getName();
      return "http:// " + identity + " " + this.id;
    } else {
      return "http:// " + this.id;
    }
  }

  static final class Subscription {
    final NotificationListener listener;
    final NotificationFilter filter;
    final Object handback;

    Subscription(NotificationListener listener, NotificationFilter filter, Object handback) {
      this.listener = listener;
      this.filter = filter;
      this.handback = handback;
    }

  }

}
