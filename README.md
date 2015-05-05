JMX-HTTP Connector
==================

A JMX connector (client and server) that runs JMX through HTTP (or HTTPS).

### What does the URL format look like?

    service:jmx:http(s)://${host}:${port}/${servlet-path}

eg. <a href="service:jmx:http://localhost:8080/jmx-http">service:jmx:http://localhost:8080/jmx-http</a>

### Why?

This connector is intended to be used in cases where you'll already have an HTTP port open. This gives it the following advantages:

 * HTTP punches through any firewall.
 * An existing port can be used.
 * HTTP is already supported by a lot of network infrastructure.
  * Piggy backs on your existing HTTP infrastructure for authentication, authorization and encryption.
 * This connector is quite lightweight:
  * The protocol runs plain Java Serialization over HTTP, not XML or even SOAP.
  * notifications are done with long poll for maximum compatibility and low latency
    * for minimal resource use servlet 3 async support is used
  * No dependencies other than servlet API and Java SE
   * The server server is 50 kb.
   * The client client is 40 kb.

### Why don't you use WebSockets?

A lot of network infrastructure does not (yet) support WebSockets. Using WebSockets would therefore negate the goal of punches through firewalls.

### What about security?

Per default no security is applied. You can either use your existing networking configuration to secure access or build a new WAR with servlet security. The WAR project contains only the `web.xml` so this is easy.
The client supports HTTP Basic authentication.

### What about servlet logging?

The servlet uses `java.util.logging` configure your server accordingly.

### What about load balancing?

You should not connect to the application through a load balancer since you want to monitor a specific server rather than a "random" one.

Protocol
--------

The client serializes each request as a command object and POSTs it to a servlet. The servlet deserializes the command and executes it. Afterwards the result is serialized and sent back to the client.

Check out the class comment of `com.github.marschall.jmxhttp.server.servlet.JmxHttpServlet` for more details.

Caveats
-------
 * Long polling may delay JVM shut down.
 * Listeners are prone to introduce memory leaks. Make sure you register the same listener object only once and use the same ObjectName for registering and unregistering.


