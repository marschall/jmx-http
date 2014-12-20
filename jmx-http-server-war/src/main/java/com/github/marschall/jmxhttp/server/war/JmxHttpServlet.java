package com.github.marschall.jmxhttp.server.war;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.lang.invoke.MethodHandles;
import java.lang.management.ManagementFactory;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.JMException;
import javax.management.MBeanServer;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.github.marschall.jmxhttp.common.command.ClassLoaderObjectInputStream;
import com.github.marschall.jmxhttp.common.command.Command;

public class JmxHttpServlet extends HttpServlet {
  
  private static final Logger LOG = Logger.getLogger(MethodHandles.lookup().lookupClass().getName());

  private volatile MBeanServer server;
  private volatile ClassLoader classLoader;

  @Override
  public void init() throws ServletException {
    super.init();
    this.server = ManagementFactory.getPlatformMBeanServer();
    this.classLoader = this.getClass().getClassLoader();
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
      result = command.execute(this.server);
    } catch (JMException | IOException e) {
      LOG.log(Level.WARNING, "exception while executing operation", e);
      result = e;
    }

    try (OutputStream out = response.getOutputStream();
        ObjectOutputStream stream = new ObjectOutputStream(out)) {
      response.setContentType("application/x-java-serialized-object");
      stream.writeObject(result);
    }
    
  }

  public void sendError(String message, Exception e, HttpServletResponse response) throws IOException {
    LOG.log(Level.SEVERE, message, e);
    response.setStatus(500);
    response.setCharacterEncoding("UTF-8");
    e.printStackTrace(response.getWriter());
  }

}
