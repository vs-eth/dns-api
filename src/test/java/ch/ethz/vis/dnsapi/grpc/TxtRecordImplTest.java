package ch.ethz.vis.dnsapi.grpc;

import ch.ethz.vis.dnsapi.netcenter.dto.CreateTxtRecordRequest;
import ch.ethz.vis.dnsapi.netcenter.dto.JsonResponse;
import ch.ethz.vis.dnsapi.netcenter.dto.SearchTxtRecordRequest;
import ch.ethz.vis.dnsapi.netcenter.dto.TxtRecord;
import ch.vseth.sip.dns.DeleteTxtRecordRequest;
import ch.vseth.sip.dns.EmptyResponse;
import ch.vseth.sip.dns.RecordOptions;
import ch.vseth.sip.dns.TxtResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.StatusRuntimeException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;

import java.io.IOException;
import java.io.StringWriter;

public class TxtRecordImplTest extends DnsImplBase {
    private static final String ID = "123456";
    private static final String VALUE = "txt-record-value";
    private static final String TXT_NAME = "some-txt-name";

    private static final ObjectMapper mapper = new ObjectMapper();

    @org.junit.Test
    public void successfullyCreateTxtRecordWithDefaultIsg() throws InterruptedException, IOException {
        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("{\"id\":\"" + ID + "\",\"fqName\":\"" + TXT_NAME + "." + DEFAULT_SUBDOMAIN + "\",\"value\":\"" + VALUE + "\"}"));

        var request = defaultCreateTxtRecordRequest().build();

        EmptyResponse response = stub.createTxtRecord(request);
        org.junit.Assert.assertNotNull(response);

        RecordedRequest rr = mockWebServer.takeRequest();
        CreateTxtRecordRequest generatedRequest = mapper.readValue(rr.getBody().inputStream(), CreateTxtRecordRequest.class);
        org.junit.Assert.assertNotNull(generatedRequest);

        assertRequiredFieldsSet(generatedRequest);
        org.junit.Assert.assertEquals(DEFAULT_ISG, generatedRequest.getIsgGroup());
    }

    @org.junit.Test
    public void successfullyCreateTxtRecordWithCustomIsg() throws InterruptedException, IOException {
        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("{\"id\":\"" + ID + "\",\"fqName\":\"" + TXT_NAME + "." + DEFAULT_SUBDOMAIN + "\",\"value\":\"" + VALUE + "\"}"));

        var request = defaultCreateTxtRecordRequest()
                .setOptions(RecordOptions.newBuilder().setIsgGroup(CUSTOM_ISG).build())
                .build();

        EmptyResponse response = stub.createTxtRecord(request);
        org.junit.Assert.assertNotNull(response);

        RecordedRequest rr = mockWebServer.takeRequest();
        CreateTxtRecordRequest generatedRequest = mapper.readValue(rr.getBody().inputStream(), CreateTxtRecordRequest.class);
        org.junit.Assert.assertNotNull(generatedRequest);

        assertRequiredFieldsSet(generatedRequest);
        org.junit.Assert.assertEquals(CUSTOM_ISG, generatedRequest.getIsgGroup());
    }

    @org.junit.Test(expected = StatusRuntimeException.class)
    public void tryCreateTxtRecordWithErrorFromBackend() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("{\"errors\": [{\"rowId\":\"undef\",\"field\":\"name\",\"errMsg\":\"Some error happened\"}]}"));

        var request = defaultCreateTxtRecordRequest().build();

        EmptyResponse response = stub.createTxtRecord(request);
    }

    @org.junit.Test
    public void successfullySearchTxtRecord() throws InterruptedException, IOException {
        StringWriter mockResponseBody = new StringWriter();
        TxtRecord record = TxtRecord.Builder.newBuilder()
                .withId(ID)
                .withFqName(TXT_NAME + "." + DEFAULT_SUBDOMAIN)
                .withValue(VALUE)
                .withTtl(DEFAULT_TTL)
                .withIsgGroup(DEFAULT_ISG)
                .withViews(DEFAULT_VIEWS)
                .build();
        mapper.writeValue(mockResponseBody, record);

        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody(mockResponseBody.toString()));

        var request = ch.vseth.sip.dns.SearchTxtRecordRequest.newBuilder()
                .setFqName(record.getFqName())
                .build();

        TxtResponse response = stub.searchTxtRecord(request);
        org.junit.Assert.assertNotNull(response);
        org.junit.Assert.assertEquals(TXT_NAME + "." + DEFAULT_SUBDOMAIN, response.getFqName());
        org.junit.Assert.assertEquals(VALUE, response.getValue());
        org.junit.Assert.assertEquals(DEFAULT_TTL, response.getOptions().getTtl());
        org.junit.Assert.assertEquals(DEFAULT_ISG, response.getOptions().getIsgGroup());
        org.junit.Assert.assertTrue(response.getOptions().getExternallyViewable());

        RecordedRequest rr = mockWebServer.takeRequest();
        SearchTxtRecordRequest generatedRequest = mapper.readValue(rr.getBody().inputStream(), SearchTxtRecordRequest.class);
        org.junit.Assert.assertNotNull(generatedRequest);
        org.junit.Assert.assertEquals(TXT_NAME + "." + DEFAULT_SUBDOMAIN, generatedRequest.getFqName());
    }

    @org.junit.Test(expected = StatusRuntimeException.class)
    public void searchTxtRecordWithoutResult() throws InterruptedException, IOException {
        StringWriter mockResponseBody = new StringWriter();
        TxtRecord record = TxtRecord.Builder.newBuilder()
                .withId(null)
                .withFqName(null)
                .build();
        mapper.writeValue(mockResponseBody, record);

        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody(mockResponseBody.toString()));

        var request = ch.vseth.sip.dns.SearchTxtRecordRequest.newBuilder()
                .setFqName(TXT_NAME + "." + DEFAULT_SUBDOMAIN)
                .build();

        TxtResponse response = stub.searchTxtRecord(request);
    }

    @org.junit.Test
    public void successfullyDeleteTxtRecord() throws InterruptedException, IOException {
        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("{\"id\":\"" + ID + "\",\"fqName\":\"" + TXT_NAME + "." + DEFAULT_SUBDOMAIN + "\",\"value\":\"" + VALUE + "\",\"netsupName\":\"" + DEFAULT_ISG + "\"}"));
        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("{\"status\":\"deleted\"}"));

        DeleteTxtRecordRequest request = defaultDeleteTxtRecordRequest().build();

        EmptyResponse response = stub.deleteTxtRecord(request);
        org.junit.Assert.assertNotNull(response);

        RecordedRequest findIdRequest = mockWebServer.takeRequest();
        TxtRecord record = mapper.readValue(findIdRequest.getBody().inputStream(), TxtRecord.class);
        org.junit.Assert.assertNotNull(record);
        org.junit.Assert.assertEquals(VALUE, record.getValue());
        org.junit.Assert.assertEquals(TXT_NAME + "." + DEFAULT_SUBDOMAIN, record.getFqName());

        RecordedRequest deletionRequest = mockWebServer.takeRequest();
        org.junit.Assert.assertEquals(DEFAULT_PATH + "txt/" + ID, deletionRequest.getPath());
        org.junit.Assert.assertEquals("DELETE", deletionRequest.getMethod());
    }

    @org.junit.Test(expected = StatusRuntimeException.class)
    public void tryDeleteTxtRecordWithErrorInSecondRequest() throws InterruptedException, IOException {
        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("{\"id\":\"" + ID + "\",\"fqName\":\"" + TXT_NAME + "." + DEFAULT_SUBDOMAIN + "\",\"value\":\"" + VALUE + "\",\"netsupName\":\"" + DEFAULT_ISG + "\"}"));
        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("{\"errors\": [{\"rowId\":\"undef\",\"field\":\"name\",\"errMsg\":\"Some error happened\"}]}"));

        DeleteTxtRecordRequest request = defaultDeleteTxtRecordRequest().build();

        EmptyResponse response = stub.deleteTxtRecord(request);
        org.junit.Assert.assertNotNull(response);

        RecordedRequest findIdRequest = mockWebServer.takeRequest();
        TxtRecord record = mapper.readValue(findIdRequest.getBody().inputStream(), TxtRecord.class);
        org.junit.Assert.assertNotNull(record);
        org.junit.Assert.assertEquals(ID, record.getId());
        org.junit.Assert.assertEquals(VALUE, record.getValue());
        org.junit.Assert.assertEquals(TXT_NAME + "." + DEFAULT_SUBDOMAIN, record.getFqName());

        RecordedRequest deletionRequest = mockWebServer.takeRequest();
        JsonResponse deletionResponse = mapper.readValue(deletionRequest.getBody().inputStream(), JsonResponse.class);
    }

    @org.junit.Test(expected = StatusRuntimeException.class)
    public void tryDeleteTxtRecordWithErrorInFirstRequest() throws InterruptedException, IOException {
        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("{\"errors\": [{\"rowId\":\"undef\",\"field\":\"name\",\"errMsg\":\"Some error happened\"}]}"));

        DeleteTxtRecordRequest request = defaultDeleteTxtRecordRequest().build();

        EmptyResponse response = stub.deleteTxtRecord(request);
        org.junit.Assert.assertNotNull(response);

        RecordedRequest findIdRequest = mockWebServer.takeRequest();
        TxtRecord record = mapper.readValue(findIdRequest.getBody().inputStream(), TxtRecord.class);
        org.junit.Assert.assertNotNull(record);
        org.junit.Assert.assertEquals(ID, record.getId());
        org.junit.Assert.assertEquals(VALUE, record.getValue());
        org.junit.Assert.assertEquals(TXT_NAME + "." + DEFAULT_SUBDOMAIN, record.getFqName());

        RecordedRequest deletionRequest = mockWebServer.takeRequest();
        JsonResponse deletionResponse = mapper.readValue(deletionRequest.getBody().inputStream(), JsonResponse.class);
    }

    @org.junit.Test(expected = StatusRuntimeException.class)
    public void tryDeleteAbsentTxtRecordWithWildcardPresent() throws InterruptedException, IOException {
        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("{\"id\":\"" + ID + "\",\"fqName\":\"" + "*." + DEFAULT_SUBDOMAIN + "\",\"value\":\"" + VALUE + "\",\"netsupName\":\"" + DEFAULT_ISG + "\"}"));

        DeleteTxtRecordRequest request = defaultDeleteTxtRecordRequest().build();

        EmptyResponse response = stub.deleteTxtRecord(request);
        org.junit.Assert.assertNotNull(response);

        RecordedRequest findIdRequest = mockWebServer.takeRequest();
        TxtRecord record = mapper.readValue(findIdRequest.getBody().inputStream(), TxtRecord.class);
        org.junit.Assert.assertNotNull(record);
        org.junit.Assert.assertEquals(ID, record.getId());
        org.junit.Assert.assertEquals(VALUE, record.getValue());
        org.junit.Assert.assertEquals("*." + DEFAULT_SUBDOMAIN, record.getFqName());

        RecordedRequest deletionRequest = mockWebServer.takeRequest();
        JsonResponse deletionResponse = mapper.readValue(deletionRequest.getBody().inputStream(), JsonResponse.class);
    }

    private ch.vseth.sip.dns.CreateTxtRecordRequest.Builder defaultCreateTxtRecordRequest() {
        return ch.vseth.sip.dns.CreateTxtRecordRequest.newBuilder()
                .setValue(VALUE)
                .setDomain(TXT_NAME + "." + DEFAULT_SUBDOMAIN);
    }

    private DeleteTxtRecordRequest.Builder defaultDeleteTxtRecordRequest() {
        return DeleteTxtRecordRequest.newBuilder()
                .setValue(VALUE)
                .setFqName(TXT_NAME + "." + DEFAULT_SUBDOMAIN);
    }

    private void assertRequiredFieldsSet(CreateTxtRecordRequest request) {
        org.junit.Assert.assertEquals(VALUE, request.getValue());
        org.junit.Assert.assertEquals(TXT_NAME, request.getTxtName());
        org.junit.Assert.assertEquals(DEFAULT_SUBDOMAIN, request.getSubdomain());
    }
}
