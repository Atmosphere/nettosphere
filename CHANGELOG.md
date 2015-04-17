# Change Log

## [Unreleased](https://github.com/Atmosphere/nettosphere/tree/HEAD)

[Full Changelog](https://github.com/Atmosphere/nettosphere/compare/nettosphere-project-2.3.0-RC3...HEAD)

**Closed issues:**

- Let AtmosphereFramework close the connection first and then Netty. [\#84](https://github.com/Atmosphere/nettosphere/issues/84)

- Allow configuring maxWebSocketFrameSize [\#83](https://github.com/Atmosphere/nettosphere/issues/83)

- onHandshake not invoked on listener [\#82](https://github.com/Atmosphere/nettosphere/issues/82)

- Add support for Pusher protocol [\#52](https://github.com/Atmosphere/nettosphere/issues/52)

## [nettosphere-project-2.3.0-RC3](https://github.com/Atmosphere/nettosphere/tree/nettosphere-project-2.3.0-RC3) (2015-02-13)

[Full Changelog](https://github.com/Atmosphere/nettosphere/compare/nettosphere-project-2.3.0-RC1...nettosphere-project-2.3.0-RC3)

**Closed issues:**

- Test fails on Windows [\#81](https://github.com/Atmosphere/nettosphere/issues/81)

## [nettosphere-project-2.3.0-RC1](https://github.com/Atmosphere/nettosphere/tree/nettosphere-project-2.3.0-RC1) (2015-01-23)

[Full Changelog](https://github.com/Atmosphere/nettosphere/compare/nettosphere-project-2.2.1...nettosphere-project-2.3.0-RC1)

**Closed issues:**

- \[Firefox\] \[NettoSphere\] ManagedService Disconnect never called [\#80](https://github.com/Atmosphere/nettosphere/issues/80)

**Merged pull requests:**

- Bumping netty version from 3.9.2 to 4.0.21.  [\#75](https://github.com/Atmosphere/nettosphere/pull/75) ([mxk1235](https://github.com/mxk1235))

## [nettosphere-project-2.2.1](https://github.com/Atmosphere/nettosphere/tree/nettosphere-project-2.2.1) (2014-12-08)

[Full Changelog](https://github.com/Atmosphere/nettosphere/compare/nettosphere-project-2.2.0...nettosphere-project-2.2.1)

**Closed issues:**

- Default host should be: 0.0.0.0 [\#77](https://github.com/Atmosphere/nettosphere/issues/77)

- PubSub Sample [\#76](https://github.com/Atmosphere/nettosphere/issues/76)

**Merged pull requests:**

- Change BridgeRuntime initialization sequence to have all config.initParams\(\) taken into account. [\#79](https://github.com/Atmosphere/nettosphere/pull/79) ([thabach](https://github.com/thabach))

- Make the I/O boss and worker Executors configurable \(to accommodate SEDA threading models\) [\#78](https://github.com/Atmosphere/nettosphere/pull/78) ([thabach](https://github.com/thabach))

## [nettosphere-project-2.2.0](https://github.com/Atmosphere/nettosphere/tree/nettosphere-project-2.2.0) (2014-07-22)

[Full Changelog](https://github.com/Atmosphere/nettosphere/compare/nettosphere-project-2.2.0-RC1...nettosphere-project-2.2.0)

**Closed issues:**

- How to configure Guice + JAX-RS ? [\#74](https://github.com/Atmosphere/nettosphere/issues/74)

## [nettosphere-project-2.2.0-RC1](https://github.com/Atmosphere/nettosphere/tree/nettosphere-project-2.2.0-RC1) (2014-06-06)

[Full Changelog](https://github.com/Atmosphere/nettosphere/compare/nettosphere-project-2.1.1...nettosphere-project-2.2.0-RC1)

**Closed issues:**

- Wrong path calculation with Config.basePath [\#73](https://github.com/Atmosphere/nettosphere/issues/73)

## [nettosphere-project-2.1.1](https://github.com/Atmosphere/nettosphere/tree/nettosphere-project-2.1.1) (2014-03-12)

[Full Changelog](https://github.com/Atmosphere/nettosphere/compare/nettosphere-project-2.1.0...nettosphere-project-2.1.1)

**Closed issues:**

- Unmodified static resource must interrupt the request dispatch [\#71](https://github.com/Atmosphere/nettosphere/issues/71)

- ChunkedWriter usage of Nettty's Wrapped ChannelBuffer eats too much memory [\#69](https://github.com/Atmosphere/nettosphere/issues/69)

- After some connections/disconnections socketio freezes on sendMessage [\#68](https://github.com/Atmosphere/nettosphere/issues/68)

-  Nettosphere doesn't work in Windows 7 [\#28](https://github.com/Atmosphere/nettosphere/issues/28)

- \[windows\] Unit test failures [\#3](https://github.com/Atmosphere/nettosphere/issues/3)

**Merged pull requests:**

- Nettosphere 2.1.x [\#72](https://github.com/Atmosphere/nettosphere/pull/72) ([jfarcand](https://github.com/jfarcand))

- Update HttpStaticFileServerHandler.java [\#70](https://github.com/Atmosphere/nettosphere/pull/70) ([fmgp](https://github.com/fmgp))

## [nettosphere-project-2.1.0](https://github.com/Atmosphere/nettosphere/tree/nettosphere-project-2.1.0) (2014-02-04)

[Full Changelog](https://github.com/Atmosphere/nettosphere/compare/nettosphere-project-2.1.0-RC2...nettosphere-project-2.1.0)

**Closed issues:**

- Add support for streaming body request  [\#67](https://github.com/Atmosphere/nettosphere/issues/67)

- Improve performance by using wrappedBuffer [\#66](https://github.com/Atmosphere/nettosphere/issues/66)

- Add support for ChannelBuffer Poolling [\#65](https://github.com/Atmosphere/nettosphere/issues/65)

- Add support for non-chunked response [\#64](https://github.com/Atmosphere/nettosphere/issues/64)

- Upgrade to Netty 3.8.0 [\#63](https://github.com/Atmosphere/nettosphere/issues/63)

- Allow disabling Chunk Aggregator [\#61](https://github.com/Atmosphere/nettosphere/issues/61)

- binary websockets not supported [\#16](https://github.com/Atmosphere/nettosphere/issues/16)

## [nettosphere-project-2.1.0-RC2](https://github.com/Atmosphere/nettosphere/tree/nettosphere-project-2.1.0-RC2) (2013-12-20)

[Full Changelog](https://github.com/Atmosphere/nettosphere/compare/nettosphere-project-2.0.2...nettosphere-project-2.1.0-RC2)

**Closed issues:**

- Expose child.tcpDelay and child.keepAlive property from Netty. [\#62](https://github.com/Atmosphere/nettosphere/issues/62)

- Add Flash Support [\#58](https://github.com/Atmosphere/nettosphere/issues/58)

- Allow disabling ChannelPipeline's ChunkedWriteHandler [\#57](https://github.com/Atmosphere/nettosphere/issues/57)

- Allow adding ChannelUpstreamHandler to the Netty's Pipeline [\#56](https://github.com/Atmosphere/nettosphere/issues/56)

- Return proper content-type header in static file handler [\#55](https://github.com/Atmosphere/nettosphere/issues/55)

- Ignore URL parameters in static file handler [\#54](https://github.com/Atmosphere/nettosphere/issues/54)

- \[Performance\] Improve Amtosphere Integration [\#53](https://github.com/Atmosphere/nettosphere/issues/53)

- binaryWrite in NettyWebSocket is always false [\#45](https://github.com/Atmosphere/nettosphere/issues/45)

- Shutdown Closing Issue [\#43](https://github.com/Atmosphere/nettosphere/issues/43)

- Improve Shutdown  [\#42](https://github.com/Atmosphere/nettosphere/issues/42)

- Nettosphere Websocket Init Params [\#39](https://github.com/Atmosphere/nettosphere/issues/39)

- \[websocket\] Return 404 when no resources are found [\#36](https://github.com/Atmosphere/nettosphere/issues/36)

- socket.io sample is throwing exception [\#33](https://github.com/Atmosphere/nettosphere/issues/33)

- Add script launcher [\#32](https://github.com/Atmosphere/nettosphere/issues/32)

- Resolution of JerseyBroadcaster OSGI Bundle [\#27](https://github.com/Atmosphere/nettosphere/issues/27)

- Add a npm-like dependencies manager [\#12](https://github.com/Atmosphere/nettosphere/issues/12)

**Merged pull requests:**

- Adapt Hello World example for current version. [\#60](https://github.com/Atmosphere/nettosphere/pull/60) ([BenRomberg](https://github.com/BenRomberg))

- fix typo [\#59](https://github.com/Atmosphere/nettosphere/pull/59) ([maliqq](https://github.com/maliqq))

## [nettosphere-project-2.0.2](https://github.com/Atmosphere/nettosphere/tree/nettosphere-project-2.0.2) (2013-10-11)

[Full Changelog](https://github.com/Atmosphere/nettosphere/compare/nettosphere-project-2.0.1...nettosphere-project-2.0.2)

## [nettosphere-project-2.0.1](https://github.com/Atmosphere/nettosphere/tree/nettosphere-project-2.0.1) (2013-10-02)

[Full Changelog](https://github.com/Atmosphere/nettosphere/compare/nettosphere-project-2.0.0...nettosphere-project-2.0.1)

**Closed issues:**

- Internet Explorer 10 websocket error [\#51](https://github.com/Atmosphere/nettosphere/issues/51)

- \[websocket\] Possible ClassCastException on close [\#50](https://github.com/Atmosphere/nettosphere/issues/50)

## [nettosphere-project-2.0.0](https://github.com/Atmosphere/nettosphere/tree/nettosphere-project-2.0.0) (2013-09-19)

[Full Changelog](https://github.com/Atmosphere/nettosphere/compare/nettosphere-project-2.0.0.RC5...nettosphere-project-2.0.0)

**Closed issues:**

- Request timeout not handled correctly. [\#49](https://github.com/Atmosphere/nettosphere/issues/49)

- Unable to create uber jar [\#47](https://github.com/Atmosphere/nettosphere/issues/47)

## [nettosphere-project-2.0.0.RC5](https://github.com/Atmosphere/nettosphere/tree/nettosphere-project-2.0.0.RC5) (2013-09-11)

[Full Changelog](https://github.com/Atmosphere/nettosphere/compare/nettosphere-project-2.0.0.RC4...nettosphere-project-2.0.0.RC5)

**Closed issues:**

- CorsInterceptor not working [\#48](https://github.com/Atmosphere/nettosphere/issues/48)

- Long Polling is very slow because of InetSocketAddress\#getHostName\(\) in NettyAtmosphereHandler [\#46](https://github.com/Atmosphere/nettosphere/issues/46)

- Error writing response for HTTP Request [\#44](https://github.com/Atmosphere/nettosphere/issues/44)

- Sources jar on nexus [\#22](https://github.com/Atmosphere/nettosphere/issues/22)

## [nettosphere-project-2.0.0.RC4](https://github.com/Atmosphere/nettosphere/tree/nettosphere-project-2.0.0.RC4) (2013-06-14)

[Full Changelog](https://github.com/Atmosphere/nettosphere/compare/nettosphere-project-2.0.0.RC3...nettosphere-project-2.0.0.RC4)

**Closed issues:**

- nettosphere-2.0.0.RC3: SSL problems with static files? \(server returns HTTP/1.1 500 Internal Server Error\)\) [\#41](https://github.com/Atmosphere/nettosphere/issues/41)

**Merged pull requests:**

- Set cookies from underlying Netty request on AtmosphereRequest [\#40](https://github.com/Atmosphere/nettosphere/pull/40) ([robrasmussen](https://github.com/robrasmussen))

## [nettosphere-project-2.0.0.RC3](https://github.com/Atmosphere/nettosphere/tree/nettosphere-project-2.0.0.RC3) (2013-05-30)

[Full Changelog](https://github.com/Atmosphere/nettosphere/compare/nettosphere-project-2.0.0.RC2...nettosphere-project-2.0.0.RC3)

**Closed issues:**

- StackOverflowError on SocketIOAtmosphereInterceptor - Cancelling the connection for AtmosphereResource [\#34](https://github.com/Atmosphere/nettosphere/issues/34)

## [nettosphere-project-2.0.0.RC2](https://github.com/Atmosphere/nettosphere/tree/nettosphere-project-2.0.0.RC2) (2013-05-03)

[Full Changelog](https://github.com/Atmosphere/nettosphere/compare/nettosphere-project-2.0.0.RC1...nettosphere-project-2.0.0.RC2)

**Closed issues:**

- java.lang.IllegalArgumentException: unsupported message type: class org.jboss.netty.handler.codec.http.DefaultHttpResponse [\#38](https://github.com/Atmosphere/nettosphere/issues/38)

- On shutdown, make sure suspended connection are properly resumed [\#37](https://github.com/Atmosphere/nettosphere/issues/37)

- Make servlet version agree with atmosphere [\#20](https://github.com/Atmosphere/nettosphere/issues/20)

- add ability to configure netty for ssl [\#17](https://github.com/Atmosphere/nettosphere/issues/17)

- Add a Server Distribution [\#11](https://github.com/Atmosphere/nettosphere/issues/11)

**Merged pull requests:**

- Set the contentLength on the AtmosphereRequest. [\#35](https://github.com/Atmosphere/nettosphere/pull/35) ([thabach](https://github.com/thabach))

## [nettosphere-project-2.0.0.RC1](https://github.com/Atmosphere/nettosphere/tree/nettosphere-project-2.0.0.RC1) (2013-03-22)

[Full Changelog](https://github.com/Atmosphere/nettosphere/compare/nettosphere-2.0.0.beta1...nettosphere-project-2.0.0.RC1)

**Closed issues:**

- fix : build request in application.js [\#31](https://github.com/Atmosphere/nettosphere/issues/31)

- fix check path on both os : unix and windows [\#30](https://github.com/Atmosphere/nettosphere/issues/30)

- the /oss.sonatype.org links to samples are returning 404's [\#29](https://github.com/Atmosphere/nettosphere/issues/29)

- socket.io sample is throwing exception [\#26](https://github.com/Atmosphere/nettosphere/issues/26)

- Autodetect and fallback transport not working. [\#24](https://github.com/Atmosphere/nettosphere/issues/24)

- Add support for byte frame [\#23](https://github.com/Atmosphere/nettosphere/issues/23)

- QueryParams are not grabbed from the url [\#18](https://github.com/Atmosphere/nettosphere/issues/18)

**Merged pull requests:**

- https://github.com/Atmosphere/nettosphere/issues/23 [\#25](https://github.com/Atmosphere/nettosphere/pull/25) ([BrynCooke](https://github.com/BrynCooke))

## [nettosphere-2.0.0.beta1](https://github.com/Atmosphere/nettosphere/tree/nettosphere-2.0.0.beta1) (2012-10-29)

[Full Changelog](https://github.com/Atmosphere/nettosphere/compare/nettosphere-project-1.4.2...nettosphere-2.0.0.beta1)

**Closed issues:**

- Nettosphere "400 Bad Request" when sending message through WebSocket \(AHC\) [\#13](https://github.com/Atmosphere/nettosphere/issues/13)

**Merged pull requests:**

- Build changes to allow more modular inclusion for embedded usage [\#21](https://github.com/Atmosphere/nettosphere/pull/21) ([jjongsma](https://github.com/jjongsma))

## [nettosphere-project-1.4.2](https://github.com/Atmosphere/nettosphere/tree/nettosphere-project-1.4.2) (2012-09-05)

[Full Changelog](https://github.com/Atmosphere/nettosphere/compare/nettosphere-project-1.4.1...nettosphere-project-1.4.2)

## [nettosphere-project-1.4.1](https://github.com/Atmosphere/nettosphere/tree/nettosphere-project-1.4.1) (2012-07-26)

[Full Changelog](https://github.com/Atmosphere/nettosphere/compare/nettosphere-project-1.4.0...nettosphere-project-1.4.1)

**Closed issues:**

- UTF-8 characters encoding not working [\#15](https://github.com/Atmosphere/nettosphere/issues/15)

- Long Polling transport closed immediatelly [\#14](https://github.com/Atmosphere/nettosphere/issues/14)

## [nettosphere-project-1.4.0](https://github.com/Atmosphere/nettosphere/tree/nettosphere-project-1.4.0) (2012-05-30)

[Full Changelog](https://github.com/Atmosphere/nettosphere/compare/nettosphere-project-1.3.2...nettosphere-project-1.4.0)

## [nettosphere-project-1.3.2](https://github.com/Atmosphere/nettosphere/tree/nettosphere-project-1.3.2) (2012-05-07)

[Full Changelog](https://github.com/Atmosphere/nettosphere/compare/nettosphere-project-1.3.1...nettosphere-project-1.3.2)

## [nettosphere-project-1.3.1](https://github.com/Atmosphere/nettosphere/tree/nettosphere-project-1.3.1) (2012-04-24)

[Full Changelog](https://github.com/Atmosphere/nettosphere/compare/nettosphere-project-1.3...nettosphere-project-1.3.1)

## [nettosphere-project-1.3](https://github.com/Atmosphere/nettosphere/tree/nettosphere-project-1.3) (2012-04-23)

[Full Changelog](https://github.com/Atmosphere/nettosphere/compare/nettosphere-1.2...nettosphere-project-1.3)

**Closed issues:**

- @Suspend lead to NoSuchMethodError: org.atmosphere.cpr.AtmosphereRequest.isAsyncStarted\(\) [\#9](https://github.com/Atmosphere/nettosphere/issues/9)

- initParam\(\) not taken into account? [\#7](https://github.com/Atmosphere/nettosphere/issues/7)

- Exceptions when suspending response [\#6](https://github.com/Atmosphere/nettosphere/issues/6)

- Nettosphere Example [\#4](https://github.com/Atmosphere/nettosphere/issues/4)

## [nettosphere-1.2](https://github.com/Atmosphere/nettosphere/tree/nettosphere-1.2) (2012-04-12)

[Full Changelog](https://github.com/Atmosphere/nettosphere/compare/nettosphere-1.1.0...nettosphere-1.2)

**Closed issues:**

- Cannot Access AtmosphereHandlers [\#10](https://github.com/Atmosphere/nettosphere/issues/10)

- Speed up / Add option to switch off autoscanning [\#8](https://github.com/Atmosphere/nettosphere/issues/8)

- Root path and resource specific path ignored [\#5](https://github.com/Atmosphere/nettosphere/issues/5)

## [nettosphere-1.1.0](https://github.com/Atmosphere/nettosphere/tree/nettosphere-1.1.0) (2012-03-19)

[Full Changelog](https://github.com/Atmosphere/nettosphere/compare/nettosphere-1.0.0...nettosphere-1.1.0)

**Closed issues:**

- test error [\#2](https://github.com/Atmosphere/nettosphere/issues/2)

- compile error [\#1](https://github.com/Atmosphere/nettosphere/issues/1)

## [nettosphere-1.0.0](https://github.com/Atmosphere/nettosphere/tree/nettosphere-1.0.0) (2012-03-02)



\* *This Change Log was automatically generated by [github_changelog_generator](https://github.com/skywinder/Github-Changelog-Generator)*