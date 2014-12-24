package com.github.marschall.jmxhttp.client.urlconnection;

interface Notifier {
  
  void connected();
  
  void closed();
  
  void exceptionOccurred(Exception exception);

}
