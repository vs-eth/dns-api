package ch.ethz.vis.dnsapi.grpc;

import ch.ethz.vis.dnsapi.netcenter.dto.CreateARecordRequest;
import ch.ethz.vis.dnsapi.netcenter.dto.XmlCreateARecordRequestWrapper;
import ch.vseth.sip.dns.DeleteARecordRequest;
import ch.vseth.sip.dns.EmptyResponse;
import ch.vseth.sip.dns.RecordOptions;
import io.grpc.StatusRuntimeException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;

import javax.xml.bind.JAXB;

public class ARecordImplTest extends DnsImplBase {
    private static final String IP = "192.0.2.10";
    private static final String IP_SUBNET = "192.0.2.0";
    private static final String IP_NAME = "some-ip-name";

    @org.junit.Test
    public void successfullyCreateARecordWithDefaultIsg() throws InterruptedException {
        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("<success>Done!</success>"));

        var request = defaultCreateARecordRequest().build();

        EmptyResponse response = stub.createARecord(request);
        org.junit.Assert.assertNotNull(response);

        RecordedRequest rr = mockWebServer.takeRequest();
        XmlCreateARecordRequestWrapper generatedWrappedRequest = JAXB.unmarshal(rr.getBody().inputStream(), XmlCreateARecordRequestWrapper.class);
        org.junit.Assert.assertNotNull(generatedWrappedRequest);

        CreateARecordRequest generatedRequest = generatedWrappedRequest.getRequest();
        org.junit.Assert.assertNotNull(generatedRequest);

        assertRequiredFieldsSet(generatedRequest);
        org.junit.Assert.assertEquals(DEFAULT_ISG, generatedRequest.getIsgGroup());
    }

    @org.junit.Test
    public void successfullyCreateARecordWithCustomIsg() throws InterruptedException {
        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("<success>Done!</success>"));

        var request = defaultCreateARecordRequest()
                .setOptions(RecordOptions.newBuilder().setIsgGroup(CUSTOM_ISG).build())
                .build();

        EmptyResponse response = stub.createARecord(request);
        org.junit.Assert.assertNotNull(response);

        RecordedRequest rr = mockWebServer.takeRequest();
        XmlCreateARecordRequestWrapper generatedWrappedRequest = JAXB.unmarshal(rr.getBody().inputStream(), XmlCreateARecordRequestWrapper.class);
        org.junit.Assert.assertNotNull(generatedWrappedRequest);

        CreateARecordRequest generatedRequest = generatedWrappedRequest.getRequest();
        org.junit.Assert.assertNotNull(generatedRequest);

        assertRequiredFieldsSet(generatedRequest);
        org.junit.Assert.assertEquals(CUSTOM_ISG, generatedRequest.getIsgGroup());
    }

    @org.junit.Test(expected = StatusRuntimeException.class)
    public void tryCreateARecordWithErrorFromBackend() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("<errors><error>Done!</error></errors>"));

        ch.vseth.sip.dns.CreateARecordRequest request = defaultCreateARecordRequest().build();

        EmptyResponse response = stub.createARecord(request);
    }

    @org.junit.Test
    public void successfullyDeleteARecord() throws InterruptedException {
        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("<success>Done!</success>"));

        DeleteARecordRequest request = defaultDeleteARecordRequest().build();

        EmptyResponse response = stub.deleteARecord(request);
        org.junit.Assert.assertNotNull(response);

        RecordedRequest rr = mockWebServer.takeRequest();
        org.junit.Assert.assertEquals(DEFAULT_PATH + "nameToIP/" + IP + "/" + IP_NAME + "." + DEFAULT_SUBDOMAIN, rr.getPath());
    }

    @org.junit.Test(expected = StatusRuntimeException.class)
    public void tryDeleteARecordWithErrorFromBackend() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("<errors><error>Error!</error></errors>"));

        DeleteARecordRequest request = defaultDeleteARecordRequest().build();

        EmptyResponse response = stub.deleteARecord(request);
    }

    private ch.vseth.sip.dns.CreateARecordRequest.Builder defaultCreateARecordRequest() {
        return ch.vseth.sip.dns.CreateARecordRequest.newBuilder()
                .setIp(IP)
                .setDomain(IP_NAME + "." + DEFAULT_SUBDOMAIN);
    }

    private DeleteARecordRequest.Builder defaultDeleteARecordRequest() {
        return DeleteARecordRequest.newBuilder()
                .setHostname(IP_NAME + "." + DEFAULT_SUBDOMAIN)
                .setIp(IP);
    }

    private void assertRequiredFieldsSet(CreateARecordRequest request) {
        org.junit.Assert.assertEquals(IP, request.getIp());
        org.junit.Assert.assertEquals(IP_NAME, request.getIpName());
        org.junit.Assert.assertEquals(DEFAULT_SUBDOMAIN, request.getSubdomain());
    }
}
