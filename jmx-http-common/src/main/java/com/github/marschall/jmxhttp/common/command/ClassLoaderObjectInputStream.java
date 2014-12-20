package com.github.marschall.jmxhttp.common.command;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;

public final class ClassLoaderObjectInputStream extends ObjectInputStream {

  private final ClassLoader classLoader;

  public ClassLoaderObjectInputStream(InputStream in, ClassLoader classLoader) throws IOException {
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