package ch.ethz.vis.dnsapi;

import ch.ethz.vis.dnsapi.exceptions.InitializationException;
import ch.ethz.vis.dnsapi.grpc.AuthConfigStrategy;
import ch.ethz.vis.dnsapi.grpc.AuthServerInterceptor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public class Config {

    private static final Logger LOG = LogManager.getLogger(Config.class);

    private static final String NETCENTER_USERNAME_KEY = "ch.ethz.vis.dnsapi.netcenter.username";

    private static final String NETCENTER_PASSWORD_KEY = "ch.ethz.vis.dnsapi.netcenter.password";

    private static final String NETCENTER_ISGGROUP_KEY = "ch.ethz.vis.dnsapi.netcenter.isgGroup";

    private static final String KEY_FILE_PATH = "ch.ethz.vis.dnsapi.keyFilePath";

    private static final String CERT_FILE_PATH = "ch.ethz.vis.dnsapi.certFilePath";

    private static final String CLIENT_ID = "ch.ethz.vis.dnsapi.clientId";

    private static final String ISSUER = "ch.ethz.vis.dnsapi.issuer";

    private static final String JWKS_URL = "ch.ethz.vis.dnsapi.jwksUrl";

    private static final String DNS_ZONES_KEY = "ch.ethz.vis.dnsapi.dnsZones";

    private String username;

    private String password;

    private String isgGroup;

    private String keyFilePath;

    private String certFilePath;

    private String clientId;

    private String issuer;

    private String jwksUrl;

    private List<String> dnsZones;

    public Config(Properties p) throws InitializationException {
        readProperties(p);
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getIsgGroup() {
        return isgGroup;
    }

    public List<String> getDnsZones() {
        return dnsZones;
    }

    private void readProperties(Properties p) throws InitializationException {
        copyPropertiesToFields(p);
        checkConfigurationValidity();
        printDebugInformation();
    }

    private void copyPropertiesToFields(Properties p) {
        this.username = p.getProperty(NETCENTER_USERNAME_KEY);
        this.password = p.getProperty(NETCENTER_PASSWORD_KEY);
        this.isgGroup = p.getProperty(NETCENTER_ISGGROUP_KEY);
        this.keyFilePath = p.getProperty(KEY_FILE_PATH, "");
        this.certFilePath = p.getProperty(CERT_FILE_PATH, "");
        this.clientId = p.getProperty(CLIENT_ID, "");
        this.issuer = p.getProperty(ISSUER, "");
        this.jwksUrl = p.getProperty(JWKS_URL, "");
        this.dnsZones = Arrays.asList(p.getProperty(DNS_ZONES_KEY).split(","));
    }

    private void checkConfigurationValidity() throws InitializationException {
        checkConfigValue(username, "username");
        checkConfigValue(password, "password");
        checkConfigValue(isgGroup, "isgGroup");
        checkConfigValue(keyFilePath, "keyFilePath");
        checkConfigValue(certFilePath, "certFilePath");
        checkConfigValue(clientId, "clientId");
        checkConfigValue(issuer, "issuer");
        checkConfigValue(jwksUrl, "jwksUrl");
        checkConfigValue(dnsZones, "dnsZones");

        if (dnsZones.isEmpty()) {
            throw new InitializationException("You cannot use this API without dns zones");
        }
    }

    private void checkConfigValue(Object value, String name) throws InitializationException {
        if (value == null) {
            throw new InitializationException(name + " must be provided");
        }
    }

    private void printDebugInformation() {
        LOG.debug("Username: " + username);
        LOG.debug("Password: " + "*".repeat(password.length()));
        LOG.debug("ISG Group: " + isgGroup);
        LOG.debug("TLS Key: " + keyFilePath);
        LOG.debug("TLS Cert: " + certFilePath);
        LOG.debug("Client ID: " + clientId);
        LOG.debug("Issuer: " + issuer);
        LOG.debug("JWKS URL: " + jwksUrl);
        LOG.debug("Known zones: " + Arrays.toString(dnsZones.toArray()));
    }

    public AuthConfigStrategy getAuthConfig() {
        if (! keyFilePath.isBlank() && ! certFilePath.isBlank()) {
            return s -> s.useTransportSecurity(new File(certFilePath), new File(keyFilePath));
        } else if (! clientId.isBlank() && ! issuer.isBlank() && ! jwksUrl.isBlank()) {
            return s -> s.intercept(new AuthServerInterceptor(clientId, issuer, jwksUrl));
        } else {
            return null;
        }
    }
}
