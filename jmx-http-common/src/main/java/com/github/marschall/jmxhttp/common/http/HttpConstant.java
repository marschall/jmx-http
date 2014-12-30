package com.github.marschall.jmxhttp.common.http;

public final class HttpConstant {

  public static final String ACTION_LISTEN = "listen";
  public static final String ACTION_UNREGISTER = "unregister";
  public static final String ACTION_REGISTER = "register";
  public static final String PARAMETER_CORRELATION_ID = "correlationId";
  public static final String PARAMETER_ACTION = "action";
  public static final String JAVA_SERIALIZED_OBJECT = "application/x-java-serialized-object";

  private HttpConstant() {
    throw new AssertionError("not instantiable");
  }

}
