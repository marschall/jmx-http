package com.github.marschall.jmxhttp.client.urlconnection;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.net.HttpURLConnection;

import com.github.marschall.jmxhttp.common.command.ClassLoaderObjectInputStream;

final class UrlConnectionUtil {

  private UrlConnectionUtil() {
    throw new AssertionError("not instantiable");
  }
  
  static Object readResponseAsObject(HttpURLConnection urlConnection, ClassLoader classLoader) throws IOException {
    int status = urlConnection.getResponseCode();
    if (status == 200) {
      try (InputStream in = urlConnection.getInputStream();
          ObjectInputStream stream = new ClassLoaderObjectInputStream(new BufferedInputStream(in), classLoader)) {
        Object result;
        try {
          result = stream.readObject();
        } catch (ClassNotFoundException e) {
          // REVIEW will trigger listeners probably ok
          throw new IOException("class not found", e);
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
      throw new IOException("http request failed with status: " + status);
    }
  }

}
