## Nettosphere: A Java WebSocket and HTTP server powered by the [Atmosphere Framework](http://github.com/Atmosphere/atmosphere) and the [Netty Framework](http://netty.io/)

The easiest way to get started with NettoSphere is to download a sample and start it. You can download the [Chat](https://oss.sonatype.org/content/repositories/snapshots/org/atmosphere/nettosphere/samples/nettosphere-chat/1.3-SNAPSHOT/) or [Jersey Chat](https://oss.sonatype.org/content/repositories/snapshots/org/atmosphere/nettosphere/samples/nettosphere-jersey-chat/1.3-SNAPSHOT/) distribution.

```bash
   % unzip nettosphere-<name>-distribution.jar
   % chmod a+x ./bin/nettosphere.sh
   % ./bin/nettosphere.sh
```

Download Nettosphere [here](https://oss.sonatype.org/content/repositories/snapshots/org/atmosphere/nettosphere/1.0.0-SNAPSHOT/) or use Maven

```xml
     <dependency>
         <groupId>org.atmosphere</groupId>
         <artifactId>nettosphere</artifactId>
         <version>1.2</version>
     </dependency>
```

All [Atmosphere](http://jfarcand.wordpress.com/2011/11/07/hitchiker-guide-to-the-atmosphere-framework-using-websocket-long-polling-and-http-streaming/) API supported. As simple as:

### Deploy a Resource (annotated using Jersey), access it using HTTP or WebSocket
```java
    Nettosphere server = new Nettosphere.Builder().config(
                 new Config.Builder()
                    .host("127.0.0.1")
                    .port(8080)
                    .resource(MyResource.class)
                    .build())
                 .build();
    server.start();
```

### Deploy an AtmosphereHandler, access it using HTTP or WebSocket
```java
    Nettosphere server = new Nettosphere.Builder().config(
                 new Config.Builder()
                    .host("127.0.0.1")
                    .port(8080)
                    .resource("/*", MyAtmosphereHandler.class)
                    .build())
                 .build();
    server.start();
```

### Deploy an AtmosphereHandler, define a WebSocket protocol
```java
    Nettosphere server = new Nettosphere.Builder().config(
                 new Config.Builder()
                    .host("127.0.0.1")
                    .port(8080)
                    .webSocketProtocol(JMSOverWebSocketProtocol.class)
                    .resource("/*", MyAtmosphereHandler.class)
                    .build())
                 .build();
    server.start();
```
### Deploy a Servlet which use Meteor
```java
    Nettosphere server = new Nettosphere.Builder().config(
                 new Config.Builder()
                    .host("127.0.0.1")
                    .port(8080)
                    .resource("/*", MyServlet.class)
                    .build())
                 .build();
    server.start();
```
### The Server can also be started using java

```java
    java -cp nettosphere-all.jar
          org.atmosphere.nettosphere.Nettosphere
                [/path/to/an/exploded/war/file] [host] [port]
```
### Using Maven and the Git repo

    mvn exec:java -Dexec.arguments='path to your exploded war file'

We are on irc.freenode.net under #atmosphere-comet and [Twitter](http://twitter.com/jfarcand)
