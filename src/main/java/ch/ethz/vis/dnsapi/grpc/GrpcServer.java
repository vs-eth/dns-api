package ch.ethz.vis.dnsapi.grpc;

import ch.ethz.vis.dnsapi.netcenter.NetcenterAPI;
import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.protobuf.services.ProtoReflectionService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.List;

public class GrpcServer {

    private static final Logger LOG = LogManager.getLogger(GrpcServer.class);

    private final NetcenterAPI netcenterAPI;

    private Server server;

    private final int port;

    private final String defaultIsg;

    private final AuthConfigStrategy authConfigStrategy;

    public GrpcServer(NetcenterAPI netcenterAPI,
                      String defaultIsg,
                      AuthConfigStrategy authConfigStrategy) {
        this.netcenterAPI = netcenterAPI;
        this.port = 50051;
        this.defaultIsg = defaultIsg;
        this.authConfigStrategy = authConfigStrategy;
    }

    public void serve(List<String> dnsZones) throws IOException {
        server = instantiateServer(dnsZones);
        server.start();
        joinServer();
    }

    private Server instantiateServer(List<String> dnsZones) {
        NettyServerBuilder builder = NettyServerBuilder.forPort(port)
                .addService(new DnsImpl(netcenterAPI, defaultIsg, dnsZones))
                .addService(ProtoReflectionService.newInstance());

        authConfigStrategy.configure(builder);
        return builder.build();
    }

    private void joinServer() {
        try {
            server.awaitTermination();
        } catch (InterruptedException e) {
            LOG.info("interrupted", e);
        }
    }
}
