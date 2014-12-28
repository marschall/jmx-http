package com.github.marschall.jmxhttp.server.servlet;

import static com.github.marschall.jmxhttp.common.http.HttpConstant.ACTION_LISTEN;
import static com.github.marschall.jmxhttp.common.http.HttpConstant.ACTION_PARAMETER;
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

  private final ConcurrentMap<Long, Deque<RemoteNotification>> pendingNotifications = new ConcurrentHashMap<>();

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
    this.pendingNotifications.clear();
    super.destroy();
  }

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    if (!request.isAsyncSupported()) {
      sendAsyncSupported(response);
      return;
    }
    String action = request.getParameter(ACTION_PARAMETER);
    if (action != null) {
      handleAction(request, response, action);
    } else {
      sendError(response, "parameter '" + ACTION_PARAMETER + "' missing");
    }
  }

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

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
      result = command.execute(this.server, null);
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
    long correlationId = getCorrelationId(request, response);
    if (correlationId == NO_CORRELATION_ID) {
      return;
    }

    if (request.getAttribute(DISPATCH) != null) {
      response.setContentType("text/plain");
      response.setCharacterEncoding("UTF-8");

      Deque<RemoteNotification> deque = this.pendingNotifications.get(correlationId);
      List<RemoteNotification> notifications = new ArrayList<>(deque.size());
      RemoteNotification notification = deque.pollFirst();
      while (notification != null) {
        notifications.add(notification);
        notification = deque.pollFirst();
      }
      sendObject(response, notifications);
    } else {
      AsyncContext asyncContext = request.startAsync(request, response);
      asyncContext.setTimeout(SECONDS.toMillis(30L));
      asyncContext.addListener(DISPATCH_ON_TIMEOUT);
    }
  }

  private void handleRegister(HttpServletResponse response) throws IOException {
    response.setContentType("text/plain");
    response.setCharacterEncoding("UTF-8");
    response.getWriter().write(Long.toString(generateCorrelationId()));
  }

  private void handleUnregister(HttpServletRequest request, HttpServletResponse response) throws IOException {
    long correlationId = getCorrelationId(request, response);
    if (correlationId == NO_CORRELATION_ID) {
      return;
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

    @Override
    public long addNotificationListener(ObjectName name, NotificationFilter filter, long listenerId, Long handbackId) throws IOException {
      // TODO Auto-generated method stub
      return 0;
    }

    @Override
    public void removeNotificationListener(ObjectName name, long listenerId) throws IOException {
      
      // TODO Auto-generated method stub
      
    }

    @Override
    public void removeNotificationListener(ObjectName name, long listenerId, NotificationFilter filter, Long objectId) throws IOException {
      // TODO Auto-generated method stub
      
    }
    
  }

  final class DispatchingNotificationListener implements NotificationListener {

    private final AsyncContext asyncContext;
    private final long correlationId;

    DispatchingNotificationListener(AsyncContext asyncContext, long correlationId) {
      this.asyncContext = asyncContext;
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


      Deque<RemoteNotification> deque = pendingNotifications.get(correlationId);
      if (deque == null) {
        deque = pendingNotifications.computeIfAbsent(correlationId, (key) -> new ConcurrentLinkedDeque<>());
      }
      deque.add(remoteNotification);

      // FIXME can't reference asyncContext
      ServletRequest suppliedRequest = asyncContext.getRequest();
      suppliedRequest.setAttribute(DISPATCH, true);
      try {
        asyncContext.dispatch();
      } catch (IllegalStateException e) {
        LOG.log(Level.WARNING, "already dispatched", e);
      }
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
