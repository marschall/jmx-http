package com.github.marschall.jmxhttp.server.servlet;

import static java.util.concurrent.TimeUnit.SECONDS;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.lang.invoke.MethodHandles;
import java.lang.management.ManagementFactory;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.Notification;
import javax.management.NotificationListener;
import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.github.marschall.jmxhttp.common.command.ClassLoaderObjectInputStream;
import com.github.marschall.jmxhttp.common.command.Command;

public class JmxHttpServlet extends HttpServlet {
  
  private static final String PARAMETER_CORRELATION_ID = "correlationId";

  private static final String ACTION_PARAMETER = "action";

  private static final String JAVA_SERIALIZED_OBJECT = "application/x-java-serialized-object";

  private static final String DISPATCH = "com.github.marschall.jmxhttp.server.servlet.dispatch";
  
  private static final Logger LOG = Logger.getLogger(MethodHandles.lookup().lookupClass().getName());

  private volatile MBeanServer server;
  private volatile ClassLoader classLoader;
  
  private final ConcurrentMap<Long, Notification> pendingNotifications = new ConcurrentHashMap<>();

  @Override
  public void init() throws ServletException {
    super.init();
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

    try (OutputStream out = response.getOutputStream();
        ObjectOutputStream stream = new ObjectOutputStream(out)) {
      response.setContentType(JAVA_SERIALIZED_OBJECT);
      stream.writeObject(result);
    }
    
  }

  public void handleAction(HttpServletRequest request, HttpServletResponse response, String action) throws IOException {
    switch (action) {
      case "register":
        response.setContentType("text/plain");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(Long.toString(generateCorrelationId()));
        return;
      case "listen":
        String correlationIdParameter = request.getParameter(PARAMETER_CORRELATION_ID);
        long correlationId;
        if (correlationIdParameter != null) {
          try {
            correlationId = Long.parseLong(correlationIdParameter);
          } catch (NumberFormatException e) {
            sendError(response, "parameter '" + PARAMETER_CORRELATION_ID + "' not numeric");
            return;
          }
        } else {
          sendError(response, "parameter '" + PARAMETER_CORRELATION_ID + "' missing");
          return;
        }
        
        if (request.getAttribute(DISPATCH) != null) {
          response.setContentType("text/plain");
          response.setCharacterEncoding("UTF-8");
          
          Notification notification = this.pendingNotifications.get(correlationId);
        } else {
          AsyncContext asyncContext = request.startAsync(request, response);
          asyncContext.setTimeout(SECONDS.toMillis(30L));
          asyncContext.addListener(new AsyncListener() {
            
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
          });
        }
        return;
      default:
        sendError(response, "unknown action: " + action);
        return;
    }
  }
  
  static final class DispatchingNotificationListener implements NotificationListener {
    
    private final AsyncContext asyncContext;

    DispatchingNotificationListener(AsyncContext asyncContext) {
      this.asyncContext = asyncContext;
    }

    @Override
    public void handleNotification(Notification notification, Object handback) {
      ServletRequest suppliedRequest = asyncContext.getRequest();
      suppliedRequest.setAttribute(DISPATCH, true);
      asyncContext.dispatch();
      
    }
    
  }
  
  private long generateCorrelationId() {
    
  }
  
  private void sendError(HttpServletResponse response, String message) throws IOException {
    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
    response.setContentType("text/plain");
    response.setCharacterEncoding("UTF-8");
    response.getWriter().write(message);
  }

  private void sendAsyncSupported(HttpServletResponse response) throws IOException {
    String message = "server misconfigured, async not supported";
    LOG.log(Level.SEVERE, message);
    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    response.setContentType("text/plain");
    response.setCharacterEncoding("UTF-8");
    response.getWriter().write(message);
  }

  private void sendError(String message, Exception e, HttpServletResponse response) throws IOException {
    LOG.log(Level.SEVERE, message, e);
    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    response.setContentType("text/plain");
    response.setCharacterEncoding("UTF-8");
    e.printStackTrace(response.getWriter());
  }

}
