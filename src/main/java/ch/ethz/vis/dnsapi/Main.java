package ch.ethz.vis.dnsapi;

import ch.ethz.vis.dnsapi.exceptions.InitializationException;
import ch.ethz.vis.dnsapi.grpc.GrpcServer;
import ch.ethz.vis.dnsapi.netcenter.NetcenterAPI;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.xml.bind.JAXBException;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class Main {

    private static final Logger LOG = LogManager.getLogger(Main.class);

    private static final String ENVIRONMENT_CONFIG_FILE = "ch.ethz.vis.dnsapi.config";

    public static void main(String[] args) {
        LOG.info("Starting application...");

        try {
            runApplication();
        } catch (Exception e) {
            LOG.error("Uncaught", e);
        }
    }

    private static void runApplication() throws IOException, InitializationException, JAXBException {
        Config config = loadConfiguration();
        GrpcServer s = instantiateServer(config);

        LOG.info("Completed startup");
        s.serve(config.getDnsZones());
    }

    private static Config loadConfiguration() throws IOException, InitializationException {
        InputStream is = openPropertiesFile();
        return loadProperties(is);
    }

    private static GrpcServer instantiateServer(Config config) throws JAXBException {
        NetcenterAPI netcenterAPI = new NetcenterAPI("https://www.netcenter.ethz.ch/netcenter/rest/",
                config.getUsername(),
                config.getPassword());

        return new GrpcServer(netcenterAPI,
                config.getIsgGroup(),
                config.getAuthConfig());
    }

    private static Config loadProperties(InputStream is) throws IOException, InitializationException {
        Properties p = new Properties();
        p.load(is);
        is.close();
        return new Config(p);
    }

    private static InputStream openPropertiesFile() throws FileNotFoundException {
        String path = System.getProperty(ENVIRONMENT_CONFIG_FILE, "/etc/dnsapi/dnsapi.properties");
        return new FileInputStream(path);
    }
}
