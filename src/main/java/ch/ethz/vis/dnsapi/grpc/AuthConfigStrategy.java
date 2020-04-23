package ch.ethz.vis.dnsapi.grpc;

import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;

public interface AuthConfigStrategy {

    void configure(NettyServerBuilder builder);

}
