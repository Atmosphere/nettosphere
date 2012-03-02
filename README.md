Nettosphere: A Java WebSocket and HTTP server powered by the [Atmosphere Framework](http://github.com/Atmosphere/atmosphere) and the [Netty Framework](http://netty.io/)
-----------------------------------------------------------------------------
Download Nettosphere [here](https://oss.sonatype.org/content/repositories/snapshots/org/atmosphere/nettosphere/1.0.0-SNAPSHOT/) or use Maven

     <dependency>
         <groupId>org.atmosphere</groupId>
         <artifactId>nettosphere</artifactId>
         <version>1.0.0</version>
     </dependency>

All [Atmosphere](http://jfarcand.wordpress.com/2011/11/07/hitchiker-guide-to-the-atmosphere-framework-using-websocket-long-polling-and-http-streaming/) API supported. As simple as:

### Deploy a Resource (annotated using Jersey), access it using HTTP or WebSocket

    NettyAtmosphereServer server = new NettyAtmosphereServer.Builder().config(
                 new Config.Builder()
                    .host("127.0.0.1")
                    .port(8080)
                    .resource(MyResource.class)
                    .build())
                 .build();
    server.start();


### Deploy an AtmosphereHandler, access it using HTTP or WebSocket

    NettyAtmosphereServer server = new NettyAtmosphereServer.Builder().config(
                 new Config.Builder()
                    .host("127.0.0.1")
                    .port(8080)
                    .resource("/*", MyAtmosphereHandler.class)
                    .build())
                 .build();
    server.start();


### Deploy an AtmosphereHandler, define a WebSocket protocol

    NettyAtmosphereServer server = new NettyAtmosphereServer.Builder().config(
                 new Config.Builder()
                    .host("127.0.0.1")
                    .port(8080)
                    .webSocketProtocol(JMSOverWebSocketProtocol.class)
                    .resource("/*", MyAtmosphereHandler.class)
                    .build())
                 .build();
    server.start();

### Deploy a Servlet which use Meteor

    NettyAtmosphereServer server = new NettyAtmosphereServer.Builder().config(
                 new Config.Builder()
                    .host("127.0.0.1")
                    .port(8080)
                    .resource("/*", MyServlet.class)
                    .build())
                 .build();
    server.start();

### The Server can also be started using java

    java -cp netosphere-all.jar
          org.atmosphere.plugin.netty.NettyAtmosphereServer
                [/path/to/an/exploded/war/file] [host] [port]

We are on irc.freenode.net under #atmosphere-comet and [Twitter](http://twitter.com/jfarcand)
