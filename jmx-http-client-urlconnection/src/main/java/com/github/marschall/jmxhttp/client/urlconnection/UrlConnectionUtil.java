package com.github.marschall.jmxhttp.client.urlconnection;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.Reader;
import java.lang.invoke.MethodHandles;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;

import javax.management.JMException;

import com.github.marschall.jmxhttp.common.command.ClassLoaderObjectInputStream;

final class UrlConnectionUtil {

  private static final Logger LOG = Logger.getLogger(MethodHandles.lookup().lookupClass().getName());

  private static int BUFFER_SIZE = 8192;

  private UrlConnectionUtil() {
    throw new AssertionError("not instantiable");
  }

  static Object readResponseAsObject(HttpURLConnection urlConnection, ClassLoader classLoader) throws IOException, JMException {
    int status = urlConnection.getResponseCode();
    if (status == 200) {
      String contentEncoding = urlConnection.getHeaderField("Content-Encoding");
      try (InputStream in = urlConnection.getInputStream();
          BufferedInputStream buffered = new BufferedInputStream(in)) {
        if ("gzip".equals(contentEncoding)) {
          try (GZIPInputStream stream = new GZIPInputStream(buffered)) {
            return readFromStream(stream, classLoader);
          }
        } else {
          return readFromStream(buffered, classLoader);
        }
      }
    } else {
      readBody(urlConnection);
      throw new IOException("http request failed with status: " + status + " body " + readBody(urlConnection));
    }
  }

  private static String readBody(HttpURLConnection urlConnection) {
    String contentEncoding = urlConnection.getContentEncoding();
    try (InputStream in = urlConnection.getInputStream()) {
      if (contentEncoding != null) {
        // we buffer in readToString -> no need to buffer here
        try (Reader reader = new InputStreamReader(in, contentEncoding)) {
          return readToString(reader);
        }
      } else {
        // we buffer in readToString -> no need to buffer here
        // convert every byte to a character of the same value
        try (Reader reader = new InputStreamReader(in, StandardCharsets.ISO_8859_1)) {
          return readToString(reader);
        }
      }
    } catch (IOException e) {
      LOG.log(Level.WARNING, "could not read response body", e);
      // the body is just for debug purposes we don't have to fail here
      return "";
    }
  }

  private static String readToString(Reader reader) throws IOException {
    StringBuilder builder = new StringBuilder();
    char[] buffer = new char[BUFFER_SIZE];
    int read;
    while ((read = reader.read(buffer)) != -1) {
      builder.append(buffer, 0, read);
    }
    return builder.toString();
  }

  private static Object readFromStream(InputStream in, ClassLoader classLoader) throws IOException, JMException {
    try (ObjectInputStream stream = new ClassLoaderObjectInputStream(in, classLoader)) {
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
  }

}
