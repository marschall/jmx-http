JMX-HTTP Connector
==================

A JMX connector (client and server) that runs JMX through HTTP.


Why
---

 * punches through any firewall
 * no additional port
 * lightweight (not tunneled through SOAP)


What about servlet logging?

Uses java.util.logging configure your server accordingly.

Why no optimization?

HTTP headers are huge.

Why no WebSockets?

A lot of snake oil network infrastructure does not (yet) support WebSockets.


