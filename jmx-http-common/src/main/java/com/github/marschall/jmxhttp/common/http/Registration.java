package com.github.marschall.jmxhttp.common.http;

import java.io.Serializable;

public final class Registration implements Serializable {

  private final long correlationId;
  private final long timeoutMilliseconds;

  public Registration(long correlationId, long timeoutMilliseconds) {
    this.correlationId = correlationId;
    this.timeoutMilliseconds = timeoutMilliseconds;
  }

  public long getCorrelationId() {
    return correlationId;
  }

  public long getTimeoutMilliseconds() {
    return timeoutMilliseconds;
  }

}
