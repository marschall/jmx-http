package com.github.marschall.jmxhttp.client.urlconnection;

import static com.github.marschall.jmxhttp.common.http.HttpConstant.ACTION_REGISTER;
import static com.github.marschall.jmxhttp.common.http.HttpConstant.ACTION_UNREGISTER;
import static com.github.marschall.jmxhttp.common.http.HttpConstant.PARAMETER_ACTION;
import static com.github.marschall.jmxhttp.common.http.HttpConstant.PARAMETER_CORRELATION_ID;
import static java.nio.charset.StandardCharsets.US_ASCII;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.invoke.MethodHandles;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.ListenerNotFoundException;
import javax.management.MBeanServerConnection;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.remote.JMXConnectionNotification;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXPrincipal;
import javax.security.auth.Subject;

/**
 * The connector creates {@link MBeanServerConnection}s and manages
 * connection {@link NotificationListener}s.
 */
final class JmxHttpConnector implements JMXConnector {

  private static final Logger LOG = Logger.getLogger(MethodHandles.lookup().lookupClass().getName());

  enum State {
    INITIAL,
    CONNECTED,
    CLOSED;
  }

  private static final AtomicInteger ID_GENERATOR = new AtomicInteger(1);

  private final AtomicLong sequenceNumberGenerator = new AtomicLong(0);

  private final URL url;

  private final int id;

  private final Lock sateLock;

  private State state;

  private JmxHttpConnection mBeanServerConnection;

  private final ListenerNotifier notifier;

  JmxHttpConnector(URL url) {
    this.url = url;
    this.id = ID_GENERATOR.incrementAndGet();
    this.sateLock = new ReentrantLock();
    this.state = State.INITIAL;
    this.notifier = new ListenerNotifier();
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
      long correlationId = getCorrelationId(credentials);
      this.mBeanServerConnection = new JmxHttpConnection(correlationId, this.url, credentials, this.notifier);
      this.notifier.connected();
    } finally {
      this.sateLock.unlock();
    }
  }

  private long getCorrelationId(Optional<String> credentials) throws IOException {
    URL registrationUrl = new URL(this.url.toString() + '?' + PARAMETER_ACTION + '=' + ACTION_REGISTER);
    HttpURLConnection urlConnection = (HttpURLConnection) registrationUrl.openConnection();
    try {
      if (credentials.isPresent()) {
        urlConnection.setRequestProperty("Authorization", credentials.get());
      }
      int status = urlConnection.getResponseCode();
      if (status == 200) {
        try (InputStream in = urlConnection.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
          String line = reader.readLine();
          if (line == null) {
            throw new IOException("correlation id missing");
          }
          try {
            return Long.parseLong(line);
          } catch (NumberFormatException e) {
            throw new IOException("could not parse correlation id: " + line);
          }
        }
      } else {
        throw new IOException("http request failed with status: " + status);
      }
    } finally {
      urlConnection.disconnect();
    }
  }

  private void unregister(Optional<String> credentials, long correlationId) throws IOException {
    URL registrationUrl = new URL(this.url.toString() + '?' + PARAMETER_ACTION + '=' + ACTION_UNREGISTER + '&' + PARAMETER_CORRELATION_ID + '=' + correlationId);
    HttpURLConnection urlConnection = (HttpURLConnection) registrationUrl.openConnection();
    try {
      if (credentials.isPresent()) {
        urlConnection.setRequestProperty("Authorization", credentials.get());
      }
      int status = urlConnection.getResponseCode();
      if (status == 200) {
        return;
      } else {
        throw new IOException("http request failed with status: " + status);
      }
    } finally {
      urlConnection.disconnect();
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
      this.notifier.closed();
      try {
        if (this.mBeanServerConnection != null) {
          this.unregister(this.mBeanServerConnection.getCredentials(), this.mBeanServerConnection.getCorrelationId());
        }
      } finally {
        this.mBeanServerConnection = null;
      }
    } finally {
      this.sateLock.unlock();
    }

  }

  @Override
  public void addConnectionNotificationListener(NotificationListener listener, NotificationFilter filter, Object handback) {
    this.notifier.addConnectionNotificationListener(listener, filter, handback);
  }

  @Override
  public void removeConnectionNotificationListener(NotificationListener listener) throws ListenerNotFoundException {
    this.notifier.removeConnectionNotificationListener(listener);
  }

  @Override
  public void removeConnectionNotificationListener(NotificationListener listener, NotificationFilter filter, Object handback) throws ListenerNotFoundException {
    this.notifier.removeConnectionNotificationListener(listener, filter, handback);
  }

  @Override
  public String getConnectionId() {
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

    @Override
    public int hashCode() {
      int prime = 31;
      int result = 17;
      result = prime * result + listener.hashCode();
      result = prime * result + Objects.hashCode(filter.hashCode());
      result = prime * result + Objects.hashCode(handback.hashCode());
      return result;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (!(obj instanceof Subscription)) {
        return false;
      }
      Subscription other = (Subscription) obj;
      return this.listener == other.listener
          && Objects.equals(this.filter, other.filter)
          && Objects.equals(this.handback, other.handback);
    }

  }

  final class ListenerNotifier implements Notifier {

    // CopyOnWriteArrayList does not support Iterator#remove
    // VisualVM calls #removeConnectionNotificationListener from
    // NotificationListener#handleNotification in the same thread
    // Should only be accessed from #listenerThread
    private final List<Subscription> listeners;

    private final BlockingDeque<Runnable> commands;

    private final Thread listenerManager;

    ListenerNotifier() {
      this.listeners = new ArrayList<>();
      this.commands = new LinkedBlockingDeque<>();
      this.listenerManager = new Thread(this::runComandLoop, "Listener-Manager for " + id);
    }

    private void runComandLoop() {
      while(true) {
        try {
          Runnable command = this.commands.take();
          command.run();
        } catch (InterruptedException e) {
          LOG.log(Level.FINE, "interrupted, shutting down", e);
        } catch (RuntimeException e) {
          LOG.log(Level.WARNING, "exception occrred while processing event", e);
        }
      }
    }

    void addConnectionNotificationListener(NotificationListener listener, NotificationFilter filter, Object handback) {
      this.commands.add(() -> {
        this.listeners.add(new Subscription(listener, filter, handback));
      });
    }

    void removeConnectionNotificationListener(NotificationListener listener) throws ListenerNotFoundException {
      
      this.commands.add(() -> {
//        boolean found = false;
        Iterator<Subscription> iterator = this.listeners.iterator();
        while (iterator.hasNext()) {
          Subscription subscription = iterator.next();
          if (subscription.listener == listener) {
            iterator.remove();
//            found = true;
          }
        }
        
        // TODO enable causes deadlock?
//        if (!found) {
//          throw new ListenerNotFoundException();
//        }
      });
    }

    void removeConnectionNotificationListener(NotificationListener listener, NotificationFilter filter, Object handback) throws ListenerNotFoundException {
      // TODO deadlock?
      
      this.commands.add(() -> {
        if (!this.listeners.remove(new Subscription(listener, filter, handback))) {
          // TODO enable causes deadlock?
//          throw new ListenerNotFoundException();
        }
      });
    }

    @Override
    public void connected() {
      this.commands.add(() -> {
        if (this.listeners.isEmpty()) {
          return;
        }

        String type = JMXConnectionNotification.OPENED;
        Object source = JmxHttpConnector.this;
        String connectionId = getConnectionId();
        long sequenceNumber = sequenceNumberGenerator.incrementAndGet();
        String message = "connection opened";
        Object userData = null;
        JMXConnectionNotification notification = new JMXConnectionNotification(type, source, connectionId, sequenceNumber, message, userData);

        sendNotification(notification);
      });
    }

    @Override
    public void closed() {
      this.commands.add(() -> {
        if (this.listeners.isEmpty()) {
          return;
        }

        String type = JMXConnectionNotification.CLOSED;
        Object source = JmxHttpConnector.this;
        String connectionId = getConnectionId();
        long sequenceNumber = sequenceNumberGenerator.incrementAndGet();
        String message = "connection closed";
        Object userData = null;
        JMXConnectionNotification notification = new JMXConnectionNotification(type, source, connectionId, sequenceNumber, message, userData);

        sendNotification(notification);
        listenerManager.interrupt();
      });
    }

    @Override
    public void exceptionOccurred(Exception exception) {
      this.commands.add(() -> {
        if (this.listeners.isEmpty()) {
          return;
        }

        String type = JMXConnectionNotification.FAILED;
        Object source = JmxHttpConnector.this;
        String connectionId = getConnectionId();
        long sequenceNumber = sequenceNumberGenerator.incrementAndGet();
        String message = "exception occurred";
        Object userData = exception;
        JMXConnectionNotification notification = new JMXConnectionNotification(type, source, connectionId, sequenceNumber, message, userData);

        sendNotification(notification);
      });
    }

    private void sendNotification(JMXConnectionNotification notification) {
      for (Subscription subscription : this.listeners) {
        NotificationFilter filter = subscription.filter;
        if (filter != null && !filter.isNotificationEnabled(notification)) {
          continue;
        }
        try {
          subscription.listener.handleNotification(notification, subscription.handback);
        } catch (RuntimeException e) {
          // make sure even is delivered to all listeners
          LOG.log(Level.WARNING, "exception occrred while delivering event to listener", e);
        }
      }
    }

  }

}
