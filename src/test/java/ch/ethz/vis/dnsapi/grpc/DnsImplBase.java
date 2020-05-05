package ch.ethz.vis.dnsapi.grpc;

import ch.ethz.vis.dnsapi.netcenter.NetcenterAPI;
import ch.vseth.sip.dns.DnsGrpc;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.testing.GrpcCleanupRule;
import okhttp3.mockwebserver.MockWebServer;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class DnsImplBase {
    final static String DEFAULT_PATH = "/netcenter/rest/";
    final static String DEFAULT_ISG = "default-isg";
    final static String DEFAULT_SUBDOMAIN = "some.subdomain.test";
    final static int    DEFAULT_TTL = 1234;
    final static List<String> DEFAULT_VIEWS = Arrays.asList("intern", "extern");

    final static String CUSTOM_ISG = "custom-isg";

    protected MockWebServer mockWebServer;
    protected DnsGrpc.DnsBlockingStub stub;

    @org.junit.Rule
    public final GrpcCleanupRule grpcCleanupRule = new GrpcCleanupRule();

    @org.junit.Before
    public void resetMockServer() throws Exception {
        mockWebServer = new MockWebServer();
        mockWebServer.requireClientAuth();

        NetcenterAPI netcenterAPI = new NetcenterAPI(
                mockWebServer.url(DEFAULT_PATH).toString(), "fake", "credentials");
        DnsImpl dnsImpl = new DnsImpl(netcenterAPI, DEFAULT_ISG, Collections.singletonList(DEFAULT_SUBDOMAIN));

        String serverName = InProcessServerBuilder.generateName();
        grpcCleanupRule.register(InProcessServerBuilder
                .forName(serverName).addService(dnsImpl).build().start());
        stub = DnsGrpc.newBlockingStub(InProcessChannelBuilder.forName(serverName).directExecutor().build());
    }
}
