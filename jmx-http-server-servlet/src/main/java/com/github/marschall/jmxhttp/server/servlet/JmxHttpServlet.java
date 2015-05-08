package com.github.marschall.jmxhttp.server.servlet;

import static com.github.marschall.jmxhttp.common.http.HttpConstant.ACTION_LISTEN;
import static com.github.marschall.jmxhttp.common.http.HttpConstant.ACTION_REGISTER;
import static com.github.marschall.jmxhttp.common.http.HttpConstant.ACTION_UNREGISTER;
import static com.github.marschall.jmxhttp.common.http.HttpConstant.JAVA_SERIALIZED_OBJECT;
import static com.github.marschall.jmxhttp.common.http.HttpConstant.PARAMETER_ACTION;
import static com.github.marschall.jmxhttp.common.http.HttpConstant.PARAMETER_CORRELATION_ID;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.io.IOException;
import java.io.InputStream;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.lang.invoke.MethodHandles;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;

import javax.management.InstanceNotFoundException;
import javax.management.JMException;
import javax.management.JMRuntimeException;
import javax.management.ListenerNotFoundException;
import javax.management.MBeanServer;
import javax.management.Notification;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.OperationsException;
import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.github.marschall.jmxhttp.common.command.ClassLoaderObjectInputStream;
import com.github.marschall.jmxhttp.common.command.Command;
import com.github.marschall.jmxhttp.common.command.NotificationRegistry;
import com.github.marschall.jmxhttp.common.http.HttpConstant;
import com.github.marschall.jmxhttp.common.http.Registration;
import com.github.marschall.jmxhttp.common.http.RemoteNotification;
import com.github.marschall.jmxhttp.common.http.HttpConstant;

/**
 * Server endpoint for tunneling JMX through HTTP.
 *
 * <h3>Basic Protocol</h3>
 * The basic protocol is a follows:
 * <ol>
 *  <li>The client <code>GET</code>s {@value HttpConstant#PARAMETER_ACTION}={@value HttpConstant#ACTION_REGISTER}
 *      to establish a pseudo session.
 *      Result will be a serialized {@link Registration} object. This object
 *      contains the parameters to use for the session. The session id and the long poll
 *      timeout to use.</li>
 *  <li>The client <code>POST</code>s a serialized {@link Command} to
 *      {@value HttpConstant#PARAMETER_CORRELATION_ID}=correlationId. The result will be a serialized Java object,
 *      maybe a serialized {@link Exception} to signal and exception occurred during processing of the command.
 *      The response by be gzip compressed.</li>
 *  <li>The client <code>GET</code>s
 *      {@value HttpConstant#PARAMETER_ACTION}={@value HttpConstant#ACTION_UNREGISTER}&amp;{@value HttpConstant#PARAMETER_CORRELATION_ID}=correlationId
 *      to end the pseudo session.</li>
 * </ol>
 *
 * <h3>Notification Protocol</h3>
 * The receive notifications with low latencies the server uses
 * <a href="http://en.wikipedia.org/wiki/Push_technology#Long_polling">long polling</a>.
 * The protocol is a follows:
 * <ol>
 *  <li>The client <code>GET</code>s
 *      {@value HttpConstant#PARAMETER_ACTION}={@value HttpConstant#ACTION_LISTEN}&amp;{@value HttpConstant#PARAMETER_CORRELATION_ID}=correlationId
 *      As soon as a notification is available the server will send a
 *      serialized {@link List} of {@link RemoteNotification}.
 *      The server will wait sending the response up to {@value #POLL_TIMEOUT_SECONDS_PARAMETER}
 *      seconds. This is servlet parameter that default to 30.
 *      Should this timeout be reached and no notifications are available
 *      the server will send serialized empty {@link List}.</li>
 * </ol>
 *
 * <h3>Misc</h3>
 *
 * <h4>Load balancing</h4>
 * You should not connect to this servlet through a load balancer
 * since you want to monitor a specific server rather than a "random" one.
 *
 * <h4>Sessions</h4>
 * This servlet does not use sessions.
 *
 * <h4>Serialization</h4>
 * In theory the servlet supports serialization but this is not tested.
 *
 */
public class JmxHttpServlet extends HttpServlet {

  private static final String POLL_TIMEOUT_SECONDS_PARAMETER = "poll-timeout-seconds";

  private static final long DEFAULT_TIMEOUT_MILLISECONDS = SECONDS.toMillis(30L);

  private static final String DISPATCH_ATTRIBUTE = "com.github.marschall.jmxhttp.server.servlet.dispatch";

  private static final String CORRELATION_ATTRIBUTE = "com.github.marschall.jmxhttp.server.servlet.correlation";

  private static final Logger LOG = Logger.getLogger(MethodHandles.lookup().lookupClass().getName());

  private static final long NO_CORRELATION_ID = -1L;

  private static final AtomicLong CORRELATION_ID_GENERATOR = new AtomicLong();

  private static final AsyncListener DISPATCH_ON_TIMEOUT = new DispatchOnTimeout();

  private volatile MBeanServer server;
  private volatile ClassLoader classLoader;

  private final ConcurrentMap<Long, Correlation> correlations = new ConcurrentHashMap<>();

  private volatile long timeoutMilliseconds;

  @Override
  public void init(ServletConfig config) throws ServletException {
    super.init(config);
    String pollTimeoutSecondsParamter = config.getInitParameter(POLL_TIMEOUT_SECONDS_PARAMETER);
    if (pollTimeoutSecondsParamter != null) {
      try {
        this.timeoutMilliseconds = SECONDS.toMillis(Long.parseLong(pollTimeoutSecondsParamter));
      } catch (NumberFormatException e) {
        LOG.log(Level.WARNING, "invalid value '" + pollTimeoutSecondsParamter + "' for servlet init parameter '" + POLL_TIMEOUT_SECONDS_PARAMETER + "'");
        this.timeoutMilliseconds = DEFAULT_TIMEOUT_MILLISECONDS;
      }
    } else {
      this.timeoutMilliseconds = DEFAULT_TIMEOUT_MILLISECONDS;
    }

    this.server = ManagementFactory.getPlatformMBeanServer();
    this.classLoader = JmxHttpServlet.class.getClassLoader();
  }

  @Override
  public void destroy() {
    for (Correlation correlation : this.correlations.values()) {
      try {
        correlation.unregisterListeners(this.server);
      } catch (OperationsException e) {
        // ignore has already been logged
      }
    }
    this.correlations.clear();
    this.server = null;
    this.classLoader = null;
    super.destroy();
  }

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    if (!request.isAsyncSupported()) {
      sendAsyncSupported(response);
      return;
    }
    String action = request.getParameter(PARAMETER_ACTION);
    if (action != null) {
      handleAction(request, response, action);
    } else {
      sendError(response, "parameter '" + PARAMETER_ACTION + "' missing");
    }
  }

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

    Correlation correlation = getCorrelation(request, response);
    if (correlation == null) {
      return;
    }

    Command<?> command;
    try (InputStream in = request.getInputStream();
        ObjectInputStream stream = new ClassLoaderObjectInputStream(in, this.classLoader)) {
      Object object = stream.readObject();
      if (object instanceof Command) {
        command = (Command<?>) object;
      } else {
        sendError(response, "not a command object " + (object == null ? " null " : object.getClass()));
        return;
      }
    } catch (ClassNotFoundException e) {
      sendError("class not found", e, response);
      return;
    }

    Object result;
    try {
      result = command.execute(this.server, correlation.registry);
    } catch (JMException | IOException | RuntimeException e) {
      LOG.log(Level.WARNING, "exception while executing operation", e);
      result = e;
    }

    sendObject(response, result);

  }

  private static void sendObject(HttpServletResponse response, Object result) throws IOException {
    if (result == null || result instanceof Serializable) {
      sendObject(response, (Serializable) result);
    } else {
      LOG.log(Level.WARNING, "not Serializable: " + result);
      sendObject(response, new JMRuntimeException("result " + result + " not Serializable"));
    }
  }
  private static void sendObject(HttpServletResponse response, Serializable result) throws IOException {
    try (OutputStream out = response.getOutputStream();
        GZIPOutputStream gzip = new GZIPOutputStream(out);
        ObjectOutputStream stream = new ObjectOutputStream(gzip)) {
      response.setContentType(JAVA_SERIALIZED_OBJECT);
      response.setHeader("Content-Encoding", "gzip");
      stream.writeObject(result);
    } catch (NotSerializableException e) {
      // various objects are exposed over JMX that are not serializable
      // in one case it's a javax.management.AttributeList with an element that's not serializable
      // the clean solution would be to buffer the result first into a local byte[]
      // instead of streaming it to the response
      LOG.log(Level.WARNING, "not Serializable(" + result.getClass() + ") " + result, e);
      throw e;
    }
  }

  public void handleAction(HttpServletRequest request, HttpServletResponse response, String action) throws IOException {
    switch (action) {
      case ACTION_REGISTER:
        handleRegister(response);
        return;
      case ACTION_UNREGISTER:
        handleUnregister(request, response);
        return;
      case ACTION_LISTEN:
        handleListen(request, response);
        return;
      default:
        sendError(response, "unknown action: " + action);
        return;
    }
  }

  /**
   * Checks if a correlation is considered stale.
   * <p>
   * This is how we deal with crashed clients or clients who get disconnected
   * from the network unexpected. We have to clean up such correlations
   * in order to avoid leaking memory.
   *
   * @param correlation the correlation
   * @return {@code true} if the correlation is considered stale
   */
  private boolean isStale(Correlation correlation) {
    // age of the correlation in milliseconds
    long age = System.currentTimeMillis() - correlation.lastUpdate;
    // if the timeout is 30 seconds and the age of the correlation is 35 seconds
    // then the next update is only 5 seconds past due
    long pastDue = age - timeoutMilliseconds;
    if (pastDue > 0L) {
      return false;
    }
    // if the the correlation is 65 seconds past due and the timeout is 30 seconds
    // then we missed 2 updates
    long missedUpdates = pastDue / timeoutMilliseconds;
    return missedUpdates <= 10L;
  }

  private void handleListen(HttpServletRequest request, HttpServletResponse response) throws IOException {
    Correlation correlation = this.getCorrelation(request, response);
    if (correlation == null) {
      return;
    }
    if (request.getAttribute(DISPATCH_ATTRIBUTE) != null) {
      response.setContentType("text/plain");
      response.setCharacterEncoding("UTF-8");

      correlation.setAsyncContext(null);
      List<RemoteNotification> notifications = correlation.drainPendingNotifications();
      sendObject(response, notifications);
    } else {
      List<RemoteNotification> notifications = correlation.drainPendingNotifications();
      if (!notifications.isEmpty()) {
        // we have pending notifications, send them directly instead of starting a long poll
        sendObject(response, notifications);
        return;
      }

      // initial request, just wait
      AsyncContext asyncContext = request.startAsync(request, response);
      correlation.setAsyncContext(asyncContext);
      asyncContext.setTimeout(this.timeoutMilliseconds);
      asyncContext.addListener(DISPATCH_ON_TIMEOUT);
      // an unregistration event may have happened while a long poll was still running
      // to we safe the correlation
      request.setAttribute(CORRELATION_ATTRIBUTE, correlation);
    }
  }

  private void handleRegister(HttpServletResponse response) throws IOException {
    long correlationId = generateCorrelationId();
    NotificationRegistry registry = new ServletNotificationRegistry(correlationId);
    Correlation previous = this.correlations.putIfAbsent(correlationId, new Correlation(registry));
    if (previous != null) {
      String message = "correlation: " + correlationId + " already registered";
      LOG.log(Level.WARNING, message);
      sendError(response, message);
      return;
    }
    Registration registration = new Registration(correlationId, this.timeoutMilliseconds);
    sendObject(response, registration);
  }

  private void handleUnregister(HttpServletRequest request, HttpServletResponse response) throws IOException {
    long correlationId = getCorrelationId(request, response);
    if (correlationId == NO_CORRELATION_ID) {
      return;
    }
    Correlation correlation = getCorrelation(request, response);
    if (correlation == null) {
      return;
    }

    this.correlations.remove(correlationId);
    try {
      correlation.unregisterListeners(this.server);
    } catch (OperationsException e) {
      // has already been logged
      sendError("unregister failed", e, response);
    }
  }

  private static long getCorrelationId(ServletRequest request, HttpServletResponse response) throws IOException {
    String correlationIdParameter = request.getParameter(PARAMETER_CORRELATION_ID);
    long correlationId;
    if (correlationIdParameter != null) {
      try {
        correlationId = Long.parseLong(correlationIdParameter);
      } catch (NumberFormatException e) {
        sendError(response, "parameter '" + PARAMETER_CORRELATION_ID + "' not numeric");
        return NO_CORRELATION_ID;
      }
    } else {
      sendError(response, "parameter '" + PARAMETER_CORRELATION_ID + "' missing");
      return NO_CORRELATION_ID;
    }
    return correlationId;
  }

  private Correlation getCorrelation(ServletRequest request, HttpServletResponse response) throws IOException {
    Object attribute = request.getAttribute(CORRELATION_ATTRIBUTE);
    if (attribute instanceof Correlation) {
      return (Correlation) attribute;
    }

    long correlationId = getCorrelationId(request, response);
    if (correlationId == NO_CORRELATION_ID) {
      return null;
    }

    Correlation correlation = this.correlations.get(correlationId);
    if (correlation == null) {
      LOG.log(Level.WARNING, "no correlation found for id: " + correlationId);
      sendError(response, "correlation " + correlationId + " missing");
      return null;
    }
    correlation.update();
    return correlation;
  }

  private static long generateCorrelationId() {
    return CORRELATION_ID_GENERATOR.incrementAndGet();
  }

  private static void sendError(HttpServletResponse response, String message) throws IOException {
    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
    response.setContentType("text/plain");
    response.setCharacterEncoding("UTF-8");
    response.getWriter().write(message);
  }

  private static void sendAsyncSupported(HttpServletResponse response) throws IOException {
    String message = "server misconfigured, async not supported";
    LOG.log(Level.SEVERE, message);
    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    response.setContentType("text/plain");
    response.setCharacterEncoding("UTF-8");
    response.getWriter().write(message);
  }

  private static void sendError(String message, Exception e, HttpServletResponse response) throws IOException {
    LOG.log(Level.SEVERE, message, e);
    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    response.setContentType("text/plain");
    response.setCharacterEncoding("UTF-8");
    e.printStackTrace(response.getWriter());
  }


  static final class Correlation {

    private Deque<RemoteNotification> pendingNotifications;

    private AsyncContext asyncContext;

    final NotificationRegistry registry;

    private final Map<Long, ListenerRegistration> listeners;

    private volatile long lastUpdate;

    Correlation(NotificationRegistry registry) {
      this.registry = registry;
      // VisualVM needs only one listener
      this.listeners = new HashMap<>(4);
      this.update();
    }

    void update() {
      lastUpdate = System.currentTimeMillis();
    }

    synchronized AsyncContext getAsyncContext() {
      return asyncContext;
    }

    synchronized void setAsyncContext(AsyncContext asyncContext) {
      this.asyncContext = asyncContext;
    }

    synchronized List<RemoteNotification> drainPendingNotifications() {
      if (this.pendingNotifications == null) {
        return Collections.emptyList();
      }
      List<RemoteNotification> result = new ArrayList<>(pendingNotifications.size());
      RemoteNotification notification = pendingNotifications.pollFirst();
      while (notification != null) {
        result.add(notification);
        notification = pendingNotifications.pollFirst();
      }
      return result;
    }

    synchronized void dispatch() {
      if (this.asyncContext == null) {
        LOG.log(Level.WARNING, "not dispatching no async context");
        return;
      }

      ServletRequest suppliedRequest = this.asyncContext.getRequest();
      suppliedRequest.setAttribute(DISPATCH_ATTRIBUTE, true);
      try {
        asyncContext.dispatch();
      } catch (IllegalStateException e) {
        LOG.log(Level.WARNING, "already dispatched", e);
      }
    }

    synchronized void addNotification(RemoteNotification notification) {
      if (this.pendingNotifications == null) {
        this.pendingNotifications = new ConcurrentLinkedDeque<>();
      }
      this.pendingNotifications.push(notification);
    }

    ListenerRegistration getListener(long listenerId) {
      return this.listeners.get(listenerId);
    }

    synchronized void unregisterListeners(MBeanServer server) throws OperationsException {
      List<OperationsException> exceptions = new ArrayList<>();
      for (Entry<Long, ListenerRegistration> entry : this.listeners.entrySet()) {
        ListenerRegistration registration = entry.getValue();
        try {
          server.removeNotificationListener(registration.name, registration.listener, registration.filter, registration.handback);
        } catch (ListenerNotFoundException | InstanceNotFoundException e) {
          LOG.log(Level.SEVERE, "could not unregister listener", e);
          if (exceptions == null) {
            exceptions = new ArrayList<>(2);
          }
          exceptions.add(e);
        }
      }
      this.listeners.clear();
      if (exceptions != null) {
        throw new OperationsException("could not unregister listener");
      }
    }

    synchronized void registerListener(long listenerId, ListenerRegistration listenerRegistration) {
      ListenerRegistration previous = this.listeners.putIfAbsent(listenerId, listenerRegistration);
      if (previous != null) {
        LOG.log(Level.WARNING, "duplicate listener exception");
      }
    }

    synchronized void removeListener(long listenerId, ListenerRegistration listenerRegistration) {
      this.listeners.remove(listenerId);
    }
  }


  static final class DispatchOnTimeout implements AsyncListener {

    @Override
    public void onTimeout(AsyncEvent event) throws IOException {
      ServletRequest suppliedRequest = event.getSuppliedRequest();
      suppliedRequest.setAttribute(DISPATCH_ATTRIBUTE, true);
      event.getAsyncContext().dispatch();
    }

    @Override
    public void onStartAsync(AsyncEvent event) throws IOException {
    }

    @Override
    public void onError(AsyncEvent event) throws IOException {
    }

    @Override
    public void onComplete(AsyncEvent event) throws IOException {
    }
  }

  final class ServletNotificationRegistry implements NotificationRegistry {

    private final long correlationId;

    ServletNotificationRegistry(long correlationId) {
      this.correlationId = correlationId;
    }

    private Correlation getCorrelation() {
      Correlation correlation = correlations.get(this.correlationId);
      if (correlation == null) {
        LOG.log(Level.WARNING, "no correlation found for id: " + correlationId);
      }
      return correlation;
    }

    @Override
    public void addNotificationListener(ObjectName name, long listenerId, NotificationFilter filter, Long handbackId) throws IOException {
      Correlation correlation = getCorrelation();
      if (correlation == null) {
        return;
      }

      Handback handback = new Handback(listenerId, handbackId);
      NotificationListener listener = new DispatchingNotificationListener(correlationId);
      ListenerRegistration listenerRegistration = new ListenerRegistration(name, listener, filter, handback);
      correlation.registerListener(listenerId, listenerRegistration);
      try {
        server.addNotificationListener(name, listener, filter, handback);
      } catch (InstanceNotFoundException e) {
        throw new InstanceNotFoundRuntimeException("instance not found", e);
      }
    }

    @Override
    public void removeNotificationListener(ObjectName name, long listenerId) throws IOException {
      Correlation correlation = getCorrelation();
      if (correlation == null) {
        return;
      }
      ListenerRegistration listenerRegistration = correlation.getListener(listenerId);
      if (listenerRegistration.name.equals(name)) {
        // TODO error
        return;
      }

      // TODO check handback null?
      correlation.removeListener(listenerId, listenerRegistration);
      try {
        server.removeNotificationListener(listenerRegistration.name, listenerRegistration.listener);
      } catch (ListenerNotFoundException | InstanceNotFoundException e) {
        throw new InstanceNotFoundRuntimeException("instance not found", e);
      }
    }

    @Override
    public void removeNotificationListener(ObjectName name, long listenerId, NotificationFilter filter, Long objectId) throws IOException {
      Correlation correlation = getCorrelation();
      if (correlation == null) {
        return;
      }
      ListenerRegistration listenerRegistration = correlation.getListener(listenerId);
      if (listenerRegistration.name.equals(name)) {
        // TODO error
        return;
      }
      correlation.removeListener(listenerId, listenerRegistration);
      try {
        server.removeNotificationListener(listenerRegistration.name, listenerRegistration.listener, listenerRegistration.filter, listenerRegistration.handback);
      } catch (ListenerNotFoundException | InstanceNotFoundException e) {
        throw new InstanceNotFoundRuntimeException("instance not found", e);
      }
    }

  }

  static final class InstanceNotFoundRuntimeException extends RuntimeException {

    InstanceNotFoundRuntimeException(String message, Throwable cause) {
      super(message, cause);
    }

  }

  final class DispatchingNotificationListener implements NotificationListener {

    private final long correlationId;

    DispatchingNotificationListener(long correlationId) {
      this.correlationId = correlationId;
    }

    @Override
    public void handleNotification(Notification notification, Object object) {

      RemoteNotification remoteNotification;
      if (object instanceof Handback) {
        Handback handback = (Handback) object;
        remoteNotification = new RemoteNotification(notification, handback.listenerId, handback.handbackId);
      } else {
        LOG.log(Level.SEVERE, "handback was not passed to listener, likely JMX implementation bug");
        return;
      }


      Correlation correlation = correlations.get(this.correlationId);
      if (correlation == null) {
        LOG.log(Level.WARNING, "no correlation found for id: " + correlationId);
        return;
      }
      correlation.addNotification(remoteNotification);

      correlation.dispatch();
    }

  }

  static final class Handback {

    final long listenerId;
    final Long handbackId;

    Handback(long listenerId, Long handbackId) {
      this.listenerId = listenerId;
      this.handbackId = handbackId;
    }

  }

  static final class ListenerRegistration {

    final ObjectName name;
    final NotificationListener listener;
    final NotificationFilter filter;
    final Object handback;

    ListenerRegistration(ObjectName name, NotificationListener listener,  NotificationFilter filter, Object handback) {
      this.name = name;
      this.listener = listener;
      this.filter = filter;
      this.handback = handback;
    }

    void unregister() {

    }

  }

}
