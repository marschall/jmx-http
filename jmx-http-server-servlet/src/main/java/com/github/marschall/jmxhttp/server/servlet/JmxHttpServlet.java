package com.github.marschall.jmxhttp.server.servlet;

import static com.github.marschall.jmxhttp.common.http.HttpConstant.ACTION_LISTEN;
import static com.github.marschall.jmxhttp.common.http.HttpConstant.PARAMETER_ACTION;
import static com.github.marschall.jmxhttp.common.http.HttpConstant.ACTION_REGISTER;
import static com.github.marschall.jmxhttp.common.http.HttpConstant.ACTION_UNREGISTER;
import static com.github.marschall.jmxhttp.common.http.HttpConstant.PARAMETER_CORRELATION_ID;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.lang.invoke.MethodHandles;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.Notification;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;
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
import com.github.marschall.jmxhttp.common.http.RemoteNotification;

public class JmxHttpServlet extends HttpServlet {

  private static final String JAVA_SERIALIZED_OBJECT = "application/x-java-serialized-object";

  private static final String DISPATCH = "com.github.marschall.jmxhttp.server.servlet.dispatch";

  private static final Logger LOG = Logger.getLogger(MethodHandles.lookup().lookupClass().getName());

  private static final long NO_CORRELATION_ID = -1L;

  private static final AtomicLong CORRELATION_ID_GENERATOR = new AtomicLong();

  private static final AsyncListener DISPATCH_ON_TIMEOUT = new DispatchOnTimeout(); 

  private volatile MBeanServer server;
  private volatile ClassLoader classLoader;

  private final ConcurrentMap<Long, Correlation> correlations = new ConcurrentHashMap<>();

  static final class Correlation {

    private Deque<RemoteNotification> pendingNotifications;

    private AsyncContext asyncContext;

    final NotificationRegistry registry;

    Correlation(NotificationRegistry registry) {
      this.registry = registry;
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
      suppliedRequest.setAttribute(DISPATCH, true);
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
  }

  @Override
  public void init(ServletConfig config) throws ServletException {
    super.init(config);
    String pollTimeoutSecondsParamter = config.getInitParameter("poll-timeout-seconds");
    if (pollTimeoutSecondsParamter != null) {

    }

    this.server = ManagementFactory.getPlatformMBeanServer();
    this.classLoader = this.getClass().getClassLoader();
  }

  @Override
  public void destroy() {
    // TODO remove listeners
    this.correlations.clear();
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
        return;
      }
    } catch (ClassNotFoundException e) {
      sendError("class not found", e, response);
      return;
    }

    Object result;
    try {
      result = command.execute(this.server, correlation.registry);
    } catch (JMException | IOException e) {
      LOG.log(Level.WARNING, "exception while executing operation", e);
      result = e;
    }

    sendObject(response, result);

  }

  private static void sendObject(HttpServletResponse response, Object result) throws IOException {
    try (OutputStream out = response.getOutputStream();
        ObjectOutputStream stream = new ObjectOutputStream(out)) {
      response.setContentType(JAVA_SERIALIZED_OBJECT);
      stream.writeObject(result);
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

  private void handleListen(HttpServletRequest request, HttpServletResponse response) throws IOException {
    Correlation correlation = this.getCorrelation(request, response);
    if (correlation == null) {
      return;
    }
    if (request.getAttribute(DISPATCH) != null) {
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


      AsyncContext asyncContext = request.startAsync(request, response);
      correlation.setAsyncContext(asyncContext);
      asyncContext.setTimeout(SECONDS.toMillis(30L));
      asyncContext.addListener(DISPATCH_ON_TIMEOUT);
    }
  }

  private void handleRegister(HttpServletResponse response) throws IOException {
    response.setContentType("text/plain");
    response.setCharacterEncoding("UTF-8");
    long correlationId = generateCorrelationId();
    NotificationRegistry registry = new ServletNotificationRegistry(correlationId);
    Correlation previous = this.correlations.putIfAbsent(correlationId, new Correlation(registry));
    if (previous != null) {
      LOG.log(Level.WARNING, "correlation: " + correlationId + " already registered");
      // TODO notify client
      return;
    }
    // TODO also send timeout
    response.getWriter().write(Long.toString(correlationId));
  }

  private void handleUnregister(HttpServletRequest request, HttpServletResponse response) throws IOException {
    long correlationId = getCorrelationId(request, response);
    if (correlationId == NO_CORRELATION_ID) {
      return;
    }
    // FIXME
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
    long correlationId = getCorrelationId(request, response);
    if (correlationId == NO_CORRELATION_ID) {
      return null;
    }

    Correlation correlation = this.correlations.get(correlationId);
    if (correlation == null) {
      LOG.log(Level.WARNING, "no correlation found for id: " + correlationId);
      // TODO send error to client
      return null;
    }
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


  static final class DispatchOnTimeout implements AsyncListener {

    @Override
    public void onTimeout(AsyncEvent event) throws IOException {
      ServletRequest suppliedRequest = event.getSuppliedRequest();
      suppliedRequest.setAttribute(DISPATCH, true);
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
//      Correlation correlation = getCorrelation();
//      if (correlation == null) {
//        return;
//      }
//
//      Handback handback = new Handback(listenerId, handbackId);
//      NotificationListener listener = new DispatchingNotificationListener(correlationId);
//      correlation.registerListener(listenerId, listener);
//      server.addNotificationListener(name, listener, filter, handback);
    }

    @Override
    public void removeNotificationListener(ObjectName name, long listenerId) throws IOException {
//      Correlation correlation = getCorrelation();
//      if (correlation == null) {
//        return;
//      }
//      NotificationListener listener = correlation.getListener(listenerId);
//      server.removeNotificationListener(name, listener);
//      correlation.removeListener(listenerId, listener);
    }

    @Override
    public void removeNotificationListener(ObjectName name, long listenerId, NotificationFilter filter, Long objectId) throws IOException {
//      Correlation correlation = getCorrelation();
//      if (correlation == null) {
//        return;
//      }
//      NotificationListener listener = correlation.getListener(listenerId);
//      Object handback;
//      server.removeNotificationListener(name, listener, filter, handback);
//      correlation.removeListener(listenerId, listener);
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

}
