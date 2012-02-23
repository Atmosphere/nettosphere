Netosphere: An HTTP/WebSocket server based on the Atmosphere Framework and Netty!
=================================================================================

Atmosphere's Meteor, AtmosphereHandler, WebSocketProtocol and Jersey Resource are supported.

Deploy an AtmosphereHandler, access it using HTTP or WebSocket

    NettyAtmosphereServer server = new NettyAtmosphereServer.Builder().config(
                 new Config.Builder()
                    .host("127.0.0.1")
                    .port(8080)
                    .handler("/*", MyAtmosphereHandler.class)
                    .build())
                 .build();
    server.start();


Deploy an AtmosphereHandler, define a WebSocket protocol

    NettyAtmosphereServer server = new NettyAtmosphereServer.Builder().config(
                 new Config.Builder()
                    .host("127.0.0.1")
                    .port(8080)
                    .webSocketProtocol(JMSOverWebSocketProtocol.class)
                    .handler("/*", MyAtmosphereHandler.class)
                    .build())
                 .build();
    server.start();

Deploy a Servlet which use Meteor

    NettyAtmosphereServer server = new NettyAtmosphereServer.Builder().config(
                 new Config.Builder()
                    .host("127.0.0.1")
                    .port(8080)
                    .meteor("/*", MyServlet.class)
                    .build())
                 .build();
    server.start();

The Server can also be started using java

    java -cp netosphere-all.jar
          org.atmosphere.plugin.netty.NettyAtmosphereServer
                [/path/to/an/exploded/war/file] [host] [port]

If you are interested, subscribe to our mailing list (http://groups.google.com/group/atmosphere-framework) for more info!  We are on irc.freenode.net under #atmosphere-comet
