Caused by: java.lang.UnsupportedOperationException: CollectionUsage threshold is not supported
	at sun.management.MemoryPoolImpl.isCollectionUsageThresholdExceeded(MemoryPoolImpl.java:242)
	
 javax.management.ReflectionException: Cannot find getter method getSslEnabledProtocols
	at org.apache.tomcat.util.modeler.ManagedBean.getGetter(ManagedBean.java:454)
Caused by: java.lang.NoSuchMethodException: org.apache.tomcat.util.net.NioEndpoint.getSslEnabledProtocols()
	at java.lang.Class.getMethod(Class.java:1778)
	
java.io.WriteAbortedException: writing aborted; java.io.NotSerializableException: org.apache.catalina.core.StandardEngine
	at java.io.ObjectInputStream.readObject0(ObjectInputStream.java:1355)
	
/Catalina/Engine org.apache.catalina.core.StandardEngine
	
Caused by: java.io.NotSerializableException: org.apache.catalina.realm.LockOutRealm
	at java.io.ObjectOutputStream.writeObject0(ObjectOutputStream.java:1184)
	at java.io.ObjectOutputStream.writeObject(ObjectOutputStream.java:348)

Caused by: java.io.NotSerializableException: org.apache.tomcat.util.net.NioEndpoint
	at java.io.ObjectOutputStream.writeObject0(ObjectOutputStream.java:1184)
	at java.io.ObjectOutputStream.writeObject(ObjectOutputStream.java:348)
	
Caused by: java.io.NotSerializableException: org.apache.coyote.RequestGroupInfo
	at java.io.ObjectOutputStream.writeObject0(ObjectOutputStream.java:1184)
	at java.io.ObjectOutputStream.writeObject(ObjectOutputStream.java:348)	

Caused by: java.lang.NoSuchMethodException: org.apache.coyote.http11.Http11NioProtocol.getNpnHandler()
	at java.lang.Class.getMethod(Class.java:1778)
	at org.apache.tomcat.util.modeler.ManagedBean.getGetter(ManagedBean.java:447)