package org.jeecg.config;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpRequest;
import org.springframework.boot.web.embedded.netty.NettyReactiveWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Configuration;

/**
 * Reactor Netty HttpTrafficHandler rejects %2F in query strings.
 * Must decode %2F back to / in query before HttpTrafficHandler.
 * Business path rewrite: {@link org.jeecg.filter.ApiServiceRouteWebFilter}.
 */
@Configuration
public class GatewayNettyUriCompatConfig
    implements WebServerFactoryCustomizer<NettyReactiveWebServerFactory> {

  /** Name of reactor-netty HttpTrafficHandler in the pipeline */
  private static final String HTTP_TRAFFIC_HANDLER = "reactor.left.httpTrafficHandler";

  private static final String URI_COMPAT_HANDLER = "gatewayUriCompat";

  @Override
  public void customize(NettyReactiveWebServerFactory factory) {
    factory.addServerCustomizers(
        httpServer ->
            httpServer.doOnChannelInit(
                (observer, channel, remoteAddress) -> {
                  if (channel.pipeline().get(URI_COMPAT_HANDLER) == null) {
                    channel
                        .pipeline()
                        .addBefore(
                            HTTP_TRAFFIC_HANDLER,
                            URI_COMPAT_HANDLER,
                            new ChannelInboundHandlerAdapter() {
                              @Override
                              public void channelRead(ChannelHandlerContext ctx, Object msg)
                                  throws Exception {
                                if (msg instanceof HttpRequest) {
                                  HttpRequest request = (HttpRequest) msg;
                                  String original = request.uri();
                                  String normalized = normalizeRequestUri(original);
                                  if (!normalized.equals(original)) {
                                    request.setUri(normalized);
                                  }
                                }
                                super.channelRead(ctx, msg);
                              }
                            });
                  }
                }));
  }

  static String normalizeRequestUri(String uri) {
    if (uri == null || uri.isEmpty()) {
      return uri;
    }

    int queryIdx = uri.indexOf('?');
    String pathPart = queryIdx >= 0 ? uri.substring(0, queryIdx) : uri;
    String queryPart = queryIdx >= 0 ? uri.substring(queryIdx) : "";

    queryPart = queryPart.replace("%2F", "/").replace("%2f", "/");

    return pathPart + queryPart;
  }
}
