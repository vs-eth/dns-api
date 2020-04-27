package ch.ethz.vis.dnsapi.grpc;

import ch.ethz.vis.dnsapi.grpc.DnsGrpc.DnsImplBase;
import ch.ethz.vis.dnsapi.netcenter.NetcenterAPI;
import ch.ethz.vis.dnsapi.netcenter.dto.*;
import ch.ethz.vis.dnsapi.util.SupplierWithExceptions;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import okhttp3.ResponseBody;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import retrofit2.Response;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class DnsImpl extends DnsImplBase {

    private static final Logger LOG = LogManager.getLogger(DnsImpl.class);

    private static final List<String> VIEW_INTERNAL = Collections.singletonList("intern");

    private static final List<String> VIEW_BOTH = Arrays.asList("intern", "extern");

    private static final int DEFAULT_TTL = 600;

    private final NetcenterAPI netcenterAPI;

    private final String defaultIsg;

    private final List<String> dnsZones;

    public DnsImpl(NetcenterAPI netcenterAPI, String defaultIsg, List<String> dnsZones) {
        this.netcenterAPI = netcenterAPI;
        this.defaultIsg = defaultIsg;
        this.dnsZones = dnsZones;
    }

    @Override
    public void createARecord(Dnsapi.CreateARecordRequest request, StreamObserver<Dnsapi.EmptyResponse> responseObserver) {
        LOG.debug("Got createARecord request");
        DnsName splittedName = splitOffZone(request.getDomain());

        if (splittedName.isZone()) {
            LOG.info("Performing netcenter workaround for zone-level records");
            splittedName.name = "+";
        }

        doRequest(responseObserver,
                () -> createARecord(request.getIp(),
                        splittedName.name,
                        splittedName.domain,
                        request.getOptions()));
        LOG.debug("Successfully handled createARecord request");
    }

    @Override
    public void deleteARecord(Dnsapi.DeleteARecordRequest request, StreamObserver<Dnsapi.EmptyResponse> responseObserver) {
        doRequest(responseObserver, () -> deleteARecord(request.getIp(), request.getHostname()));
    }

    @Override
    public void createCNameRecord(Dnsapi.CreateCNameRecordRequest request, StreamObserver<Dnsapi.EmptyResponse> responseObserver) {
        LOG.debug("Got createCNameRecord request");
        DnsName splittedName = splitOffZone(request.getDomain());

        if (splittedName.isZone()) {
            LOG.warn("You cannot create cnames for domains which are a zone");
            responseObserver.onError(new StatusException(Status.INVALID_ARGUMENT));
            return;
        }

        doRequest(responseObserver,
                () -> createCNameRecord(request.getHostname(),
                        splittedName.name,
                        splittedName.domain,
                        request.getOptions()));
    }

    @Override
    public void deleteCNameRecord(Dnsapi.DeleteCNameRecordRequest request, StreamObserver<Dnsapi.EmptyResponse> responseObserver) {
        doRequest(responseObserver, () -> deleteCNameRecord(request.getAlias()));
    }

    @Override
    public void createTxtRecord(Dnsapi.CreateTxtRecordRequest request, StreamObserver<Dnsapi.EmptyResponse> responseObserver) {
        LOG.debug("Got createTxtameRecord request");
        DnsName splittedName = splitOffZone(request.getDomain());
        doRequest(responseObserver,
                () -> createTxtRecord(
                        splittedName.name,
                        splittedName.domain,
                        request.getValue(),
                        request.getOptions()));
    }

    @Override
    public void searchTxtRecord(Dnsapi.SearchTxtRecordRequest request, StreamObserver<Dnsapi.TxtResponse> responseObserver) {
        doRequest(responseObserver, () -> searchTxtRecord(request.getFqName()));
    }

    @Override
    public void deleteTxtRecord(Dnsapi.DeleteTxtRecordRequest request, StreamObserver<Dnsapi.EmptyResponse> responseObserver) {
        doRequest(responseObserver, () -> deleteTxtRecord(request.getFqName(), request.getValue()));
    }

    private <T> void doRequest(StreamObserver<T> responseObserver, SupplierWithExceptions<T, StatusException> response) {
        try {
            responseObserver.onNext(response.accept());
            responseObserver.onCompleted();
        } catch (StatusException e) {
            responseObserver.onError(e);
        }
    }

    private Dnsapi.EmptyResponse createARecord(String ip, String ipName, String subdomain, Dnsapi.RecordOptions options) throws StatusException {
        LOG.debug("Create A: " + ipName + "." + subdomain + " -> " + ip);

        checkParameterPresence(ip, "ip");
        checkParameterPresence(ipName, "ipName");
        checkParameterPresence(subdomain, "subdomain");

        CreateARecordRequest request = CreateARecordRequest.Builder.newBuilder()
                .withIp(ip)
                .withIpName(ipName)
                .withSubdomain(subdomain)
                .withIsgGroup(options.getIsgGroup().isEmpty() ? defaultIsg : options.getIsgGroup())
                .withViews(options.getExternallyViewable() ? VIEW_BOTH : VIEW_INTERNAL)
                .withTtl(options.getTtl() > 0 ? options.getTtl() : 600)
                .withReverse(false) // FIXME: Find sensible defaults.
                .build();

        return checkResponse(() -> netcenterAPI.getARecordManager().CreateARecord(
                new XmlCreateARecordRequestWrapper(request)).execute());
    }

    private Dnsapi.EmptyResponse deleteARecord(String ip, String fqName) throws StatusException {
        LOG.debug("Delete A: " + fqName + " -> " + ip);
        checkParameterPresence(ip, "ip");
        checkParameterPresence(fqName, "fqName");
        return checkResponse(() -> netcenterAPI.getARecordManager().DeleteARecord(ip, fqName).execute());
    }

    private Dnsapi.EmptyResponse createCNameRecord(String hostname, String aliasName, String subdomain, Dnsapi.RecordOptions options) throws StatusException {
        LOG.debug("Create CNAME: " + aliasName + "." + subdomain + " -> " + hostname);

        checkParameterPresence(hostname, "hostname");
        checkParameterPresence(aliasName, "aliasName");
        checkParameterPresence(subdomain, "subdomain");

        CreateCNameRecordRequest request = CreateCNameRecordRequest.Builder.newBuilder()
                .withHostname(hostname)
                .withAliasName(aliasName)
                .withSubdomain(subdomain)
                .withIsgGroup(options.getIsgGroup().isEmpty() ? defaultIsg : options.getIsgGroup())
                .withViews(options.getExternallyViewable() ? VIEW_BOTH : VIEW_INTERNAL)
                .withTtl(options.getTtl() > 0 ? options.getTtl() : DEFAULT_TTL)
                .build();

        return checkResponse(() -> netcenterAPI.getCNameRecordManager().CreateCNameRecord(
                new XmlCreateCNameRecordRequestWrapper(request)).execute());
    }

    private Dnsapi.EmptyResponse deleteCNameRecord(String alias) throws StatusException {
        LOG.debug("Delete CNAME: " + alias);
        checkParameterPresence(alias, "alias");
        return checkResponse(() -> netcenterAPI.getCNameRecordManager().DeleteCNameRecord(alias).execute());
    }

    private Dnsapi.EmptyResponse createTxtRecord(String txtName, String subdomain, String value, Dnsapi.RecordOptions options) throws StatusException {
        LOG.debug("Create TXT: " + txtName + "." + subdomain + " -> " + value);

        checkParameterPresence(txtName, "txtName");
        checkParameterPresence(subdomain, "subdomain");
        checkParameterPresence(value, "value");

        CreateTxtRecordRequest request = CreateTxtRecordRequest.Builder.newBuilder()
                .withTxtName(txtName)
                .withSubdomain(subdomain)
                .withValue(value)
                .withIsgGroup(options.getIsgGroup().isEmpty() ? defaultIsg : options.getIsgGroup())
                .withViews(options.getExternallyViewable() ? VIEW_BOTH : VIEW_INTERNAL)
                .withTtl(options.getTtl() > 0 ? options.getTtl() : DEFAULT_TTL)
                .build();

        checkTxtJsonResponse(() -> netcenterAPI.getTxtRecordManager().CreateTxtRecord(request).execute());

        return Dnsapi.EmptyResponse.getDefaultInstance();
    }

    private Dnsapi.TxtResponse searchTxtRecord(String fqName) throws StatusException {
        LOG.debug("Search TXT: " + fqName);

        checkNonEmptyString(fqName, "fqName");

        SearchTxtRecordRequest request = SearchTxtRecordRequest.Builder.newBuilder()
                .withFqName(fqName).build();

        TxtRecord record = checkTxtJsonResponse(() -> netcenterAPI.getTxtRecordManager().SearchTxtRecord(request).execute());

        if (record.getId() == null || record.getFqName() == null) {
            LOG.debug("TXT record not found");
            throw new StatusException(Status.NOT_FOUND.withDescription("TXT record not found"));
        }

        return Dnsapi.TxtResponse.newBuilder()
                .setFqName(record.getFqName())
                .setValue(record.getValue())
                .setOptions(Dnsapi.RecordOptions.newBuilder()
                        .setTtl(record.getTtl())
                        .setIsgGroup(record.getIsgGroup())
                        .setExternallyViewable(record.getViews().contains("extern"))
                        .build())
                .build();
    }

    private Dnsapi.EmptyResponse deleteTxtRecord(String fqName, String value) throws StatusException {
        LOG.debug("Delete TXT: " + fqName + " -> " + value);

        checkParameterPresence(fqName, "fqName");
        checkParameterPresence(value, "value");

        SearchTxtRecordRequest request = SearchTxtRecordRequest.Builder.newBuilder()
                .withFqName(fqName)
                .withValue(value)
                .build();

        TxtRecord record = checkTxtJsonResponse(() -> netcenterAPI.getTxtRecordManager().SearchTxtRecord(request).execute());

        LOG.debug("Found TXT record: " + record.getFqName() + " -> " + record.getValue());
        if (record.getId() == null || record.getFqName() == null || record.getValue() == null) {
            LOG.warn("TXT record was empty");
            throw new StatusException(Status.NOT_FOUND.withDescription("TXT record not found"));
        } else if (!record.getFqName().equals(fqName) || !record.getValue().equals(value)) {
            LOG.warn("TXT record does not match given parameters");
            throw new StatusException(Status.NOT_FOUND.withDescription("TXT record not found"));
        }

        LOG.debug("Got TXT id: " + record.getId());
        try {
            Response<JsonResponse> deleteTxtResponse = netcenterAPI.getTxtRecordManager().DeleteTxtRecord(record.getId()).execute();
            checkResponse(deleteTxtResponse);

            JsonResponse body = deleteTxtResponse.body();
            if (body != null && body.getErrors() != null) {
                String errorMsg = body.getErrors().stream().map(JsonError::getErrorMsg).collect(Collectors.joining(", "));
                LOG.debug("Got errors from API: " + errorMsg);
                throw new StatusException(Status.INTERNAL.withDescription("Got errors from API:" + errorMsg));
            }
        } catch (IOException e) {
            LOG.error("Unexpected IOException: " + e);
            throw new StatusException(Status.INTERNAL.withDescription("Error relaying request to API"));
        }

        return Dnsapi.EmptyResponse.getDefaultInstance();
    }

    private void checkParameterPresence(Object param, String paramName) throws StatusException {
        if (param == null) {
            String message = "Missing parameter " + paramName;
            LOG.debug(message);
            throw new StatusException(Status.INVALID_ARGUMENT.withDescription(message));
        }
    }

    private void checkNonEmptyString(String param, String paramName) throws StatusException {
        if (param == null || param.isBlank()) {
            String message = "Missing parameter " + paramName;
            LOG.debug(message);
            throw new StatusException(Status.INVALID_ARGUMENT.withDescription(message));
        }
    }

    private <T> Dnsapi.EmptyResponse checkResponse(SupplierWithExceptions<Response<T>, IOException> request) throws StatusException {
        try {
            Response<T> response = request.accept();
            return checkResponse(response);
        } catch (IOException e) {
            LOG.error("Unexpected", e);
            throw new StatusException(Status.INTERNAL.withDescription("error relaying request to API"));
        }
    }

    private <T> Dnsapi.EmptyResponse checkResponse(Response<T> response) throws StatusException, IOException {
        if (!response.isSuccessful()) {
            ResponseBody errorBody = response.errorBody();
            if (errorBody != null) {
                String error = errorBody.string();
                LOG.debug("Error in response: " + error);
            } else {
                LOG.debug("Empty error in response");
            }
            throw new StatusException(Status.INTERNAL.withDescription("error relaying request to API"));
        }


        return Dnsapi.EmptyResponse.getDefaultInstance();
    }

    private TxtRecord checkTxtJsonResponse(SupplierWithExceptions<Response<TxtResponse>, IOException> responseSupplier) throws StatusException {
        try {
            Response<TxtResponse> response = responseSupplier.accept();
            checkResponse(response);
            TxtResponse body = response.body();

            if (body == null) {
                LOG.error("Got empty body from TXT api");
                throw new StatusException(Status.INTERNAL.withDescription("Got invalid response from API"));
            } else if (body.getErrors() != null) {
                String errorMsg = body.getErrors().stream().map(JsonError::getErrorMsg).collect(Collectors.joining(", "));
                LOG.debug("Got errors from API: " + errorMsg);
                throw new StatusException(Status.INTERNAL.withDescription("Got errors from API: " + errorMsg));
            } else if (body.getTxtRecord() == null) {
                LOG.error("Got null record from API");
                throw new StatusException(Status.INTERNAL.withDescription("Got invalid response from API"));
            }
            return body.getTxtRecord();
        } catch (IOException e) {
            LOG.error("Unexpected", e);
            throw new StatusException(Status.INTERNAL.withDescription("error relaying request to API"));
        }
    }

    DnsName splitOffZone(String name) {
        Optional<String> domain = dnsZones.stream()
                .filter(name::endsWith)
                .max(Comparator.comparingInt(String::length));

        if (domain.isEmpty()) {
            LOG.error("Requested record for name '" + name + "', but no zone is configured for it");
            throw new StatusRuntimeException(Status.INVALID_ARGUMENT);
        } else {
            DnsName result = new DnsName();
            result.domain = domain.get();
            result.name = name.replaceAll("\\." + result.domain + "$", "");

            if (result.name.equals(name)) {
                result.name = "";
            }
            return result;
        }
    }

    static final class DnsName {

        public String name;

        public String domain;

        public boolean isZone() {
            return name.isBlank();
        }
    }
}
