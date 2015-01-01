package com.github.marschall.jmxhttp.client.urlconnection;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.util.zip.GZIPInputStream;

import javax.management.JMException;

import com.github.marschall.jmxhttp.common.command.ClassLoaderObjectInputStream;

final class UrlConnectionUtil {

  private UrlConnectionUtil() {
    throw new AssertionError("not instantiable");
  }
  
  static Object readResponseAsObject(HttpURLConnection urlConnection, ClassLoader classLoader) throws IOException, JMException {
    int status = urlConnection.getResponseCode();
    if (status == 200) {
      try (InputStream in = urlConnection.getInputStream();
          GZIPInputStream gzip = new GZIPInputStream(new BufferedInputStream(in));
          ObjectInputStream stream = new ClassLoaderObjectInputStream(gzip, classLoader)) {
        Object result;
        try {
          result = stream.readObject();
        } catch (ClassNotFoundException e) {
          // REVIEW will trigger listeners probably ok
          throw new IOException("class not found", e);
        }
        if (result instanceof RuntimeException) {
          throw (RuntimeException) result;
        }
        if (result instanceof IOException) {
          throw (IOException) result;
        }
        if (result instanceof JMException) {
          throw (JMException) result;
        }
        if (result instanceof Exception) {
          // REVIEW will trigger listeners, not sure if intended
          throw new IOException("exception occurred on server", (Exception) result);
          //          throw (Exception) result;
        } else {
          return result;
        }
      }
    } else {
      // TODO read body
      StringBuilder builder = new StringBuilder();
      char[] buffer = new char[1024];
      try (InputStream in = urlConnection.getInputStream();
          Reader reader = new InputStreamReader(in)) {
        int read = reader.read(buffer);
        while (read != -1) {
          builder.append(buffer, 0, read);
          read = reader.read(buffer);
        }
      }
      System.out.println("XXXXXXXXXXXXXX");
      System.out.println(builder);
      System.out.println("XXXXXXXXXXXXXX");
      throw new IOException("http request failed with status: " + status);
    }
  }

}
