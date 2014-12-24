package com.github.marschall.jmxhttp.server.sunnet;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpServer;

public class JmxHttpServer {
  
  private CopyOnWriteArrayList<HttpServer> servers;
  private final int port;

  public JmxHttpServer(int port) {
    this.port = port;
    this.servers = new CopyOnWriteArrayList<>();
  }

  public void start() throws IOException {
    int backlog = 0; // system default
    Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
    while (networkInterfaces.hasMoreElements()) {
      NetworkInterface networkInterface = networkInterfaces.nextElement();
      Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
      while (inetAddresses.hasMoreElements()) {
        InetAddress inetAddress = inetAddresses.nextElement();
        HttpServer server = HttpServer.create(new InetSocketAddress(inetAddress, port), backlog);
        
        servers.add(server);
        
        server.createContext("/", new JmxHttpHandler());
        server.setExecutor(null); // creates a default executor
        server.start();
      }
      
    }
  }
  
  public void stop() {
    
    for (HttpServer server : servers) {
      server.stop((int) TimeUnit.SECONDS.toSeconds(1L));
    }
    
  }
  
}
