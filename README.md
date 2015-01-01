JMX-HTTP Connector
==================

A JMX connector (client and server) that runs JMX through HTTP (or HTTPS).

### Why?

This connector is intended to be used in cases where you'll already have an HTTP port open. This gives it the following advantages:

 * HTTP punches through any firewall. In general .
 * An existing port can be used.
 * HTTP is already supported by a lot of network infrastructure.
  * Piggy backs on your existing HTTP infrastructure for authentication, authorization and encryption.
 * This connector is quite lightweight:
  * The protocol runs plain Java Serialization over HTTP, not XML or even SOAP.
  * notifications are done with long poll for maximum compatibility and low latency
    * for minimal resource use servlet 3 async support is used
  * No dependencies other than servlet API and Java SE
   * The server server is XX kb.
   * The client client is XX kb.

### Why don't you use WebSockets?

A lot of network infrastructure does not (yet) support WebSockets. Using WebSockets would therefore negate the goal of punches through firewalls.

### What about security?

Per default no security is applied. You can either you your existing networking configuration to secure access or build a new WAR with servlet security. The WAR project contains only the `web.xml` so this is easy.
The client supports HTTP Basic authentication.

### What about servlet logging?

The servlet uses `java.util.logging` configure your server accordingly.

Protocol Details
----------------

The client serializes each request as a command object and POSTs it to a servlet. The servlet deserializes the command and executes it. Afterwards the result is serialized and sent back to the client.

Caveats
-------
 * long polling may delay JVM shut down

TODO
-----
Caused by: java.lang.UnsupportedOperationException: CollectionUsage threshold is not supported
	at sun.management.MemoryPoolImpl.isCollectionUsageThresholdExceeded(MemoryPoolImpl.java:242)
	
 javax.management.ReflectionException: Cannot find getter method getSslEnabledProtocols
	at org.apache.tomcat.util.modeler.ManagedBean.getGetter(ManagedBean.java:454)
Caused by: java.lang.NoSuchMethodException: org.apache.tomcat.util.net.NioEndpoint.getSslEnabledProtocols()
	at java.lang.Class.getMethod(Class.java:1778)
	
java.io.WriteAbortedException: writing aborted; java.io.NotSerializableException: org.apache.catalina.core.StandardEngine
	at java.io.ObjectInputStream.readObject0(ObjectInputStream.java:1355)

