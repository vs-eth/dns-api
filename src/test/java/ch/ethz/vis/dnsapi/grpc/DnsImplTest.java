package ch.ethz.vis.dnsapi.grpc;

import ch.ethz.vis.dnsapi.netcenter.NetcenterAPI;
import io.grpc.StatusRuntimeException;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.xml.bind.JAXBException;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DnsImplTest {

    private DnsImpl uut;

    private NetcenterAPI netcenterAPI;

    @BeforeEach
    public void setup() throws JAXBException {
        MockWebServer mockWebServer = new MockWebServer();
        mockWebServer.requireClientAuth();

        netcenterAPI = new NetcenterAPI(
                mockWebServer.url(DnsImplBase.DEFAULT_PATH).toString(), "fake", "credentials");
        uut = new DnsImpl(netcenterAPI, DnsImplBase.DEFAULT_ISG, Arrays.asList("domain.example", "subdomain.domain.example"));
    }

    @Test
    public void fdsa() {
        uut = new DnsImpl(netcenterAPI, DnsImplBase.DEFAULT_ISG, Collections.singletonList("exbeerience.ch"));

        DnsImpl.DnsName result = uut.splitOffZone("exbeerience.ch");

        assertEquals("", result.name);
        assertEquals("exbeerience.ch", result.domain);
    }

    @Test
    public void splitValidDomain() {
        String input = "test.domain.example";

        DnsImpl.DnsName result = uut.splitOffZone(input);

        assertEquals("test", result.name);
        assertEquals("domain.example", result.domain);
    }

    @Test
    public void longerDomainGetsPrecedence() {
        String input = "test.subdomain.domain.example";

        DnsImpl.DnsName result = uut.splitOffZone(input);

        assertEquals("test", result.name);
        assertEquals("subdomain.domain.example", result.domain);
    }

    @Test
    public void invalidDomainIsRejected() {
        String input = "test.unknown.example";

        assertThrows(StatusRuntimeException.class, () -> uut.splitOffZone(input));
    }
}