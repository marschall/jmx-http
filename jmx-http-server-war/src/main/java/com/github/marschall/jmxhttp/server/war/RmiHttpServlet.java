package com.github.marschall.jmxhttp.server.war;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.OutputStream;
import java.lang.invoke.MethodHandles;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.MBeanServer;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.github.marschall.jmxhttp.common.command.Command;

public class RmiHttpServlet extends HttpServlet {
  
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
    
    Command command;
    
    try (InputStream in = request.getInputStream();
        ObjectInputStream stream = new ClassLoaderObjectInputStream(in, this.classLoader)) {
      Object object = stream.readObject();
      if (object instanceof Command) {
        command = (Command) object;
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
    } catch (IOException e) {
      sendError("command execution failed", e, response);
    }

    try (OutputStream out = response.getOutputStream();
        ObjectOutputStream stream = new ObjectOutputStream(out)) {
      response.setContentType("application/x-java-serialized-object");
      stream.writeObject(stream);
    }
    
  }

  public void sendError(String message, Exception e, HttpServletResponse response) throws IOException {
    LOG.log(Level.SEVERE, message, e);
    response.setStatus(500);
    response.setCharacterEncoding("UTF-8");
    e.printStackTrace(response.getWriter());
  }

  static final class ClassLoaderObjectInputStream extends ObjectInputStream {

    private final ClassLoader classLoader;

    ClassLoaderObjectInputStream(InputStream in, ClassLoader classLoader) throws IOException {
      super(in);
      this.classLoader = classLoader;
    }

    @Override
    protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
      String name = desc.getName();
      try {
        return Class.forName(name, false, this.classLoader);
      } catch (ClassNotFoundException ex) {
        return super.resolveClass(desc);
      }
    }

    protected Class<?> resolveProxyClass(String[] interfaces) throws IOException, ClassNotFoundException {
      ClassLoader nonPublicLoader = null;
      boolean hasNonPublicInterface = false;

      // define proxy in class loader of non-public interface(s), if any
      Class<?>[] classObjs = new Class<?>[interfaces.length];
      for (int i = 0; i < interfaces.length; i++) {
        Class<?> clazz = Class.forName(interfaces[i], false, this.classLoader);
        if ((clazz.getModifiers() & Modifier.PUBLIC) == 0) {
          if (hasNonPublicInterface) {
            if (nonPublicLoader != clazz.getClassLoader()) {
              throw new IllegalAccessError("conflicting non-public interface class loaders");
            }
          } else {
            nonPublicLoader = clazz.getClassLoader();
            hasNonPublicInterface = true;
          }
        }
        classObjs[i] = clazz;
      }
      try {
        ClassLoader loader = hasNonPublicInterface ? nonPublicLoader : this.classLoader;
        return Proxy.getProxyClass(loader, classObjs);
      } catch (IllegalArgumentException e) {
        throw new ClassNotFoundException(null, e);
      }
    }

  }

}
