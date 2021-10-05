import org.apache.ignite.Ignition;
import org.apache.ignite.cache.query.ScanQuery;
import org.apache.ignite.client.ClientCache;
import org.apache.ignite.client.IgniteClient;
import org.apache.ignite.configuration.ClientConfiguration;
import org.apache.ignite.internal.IgniteProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.Server;

import javax.cache.Cache;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static server.Server.sendReport;

public class ThinClient {
    private static final Logger LOG = LoggerFactory.getLogger(ThinClient.class);
    static final String CLASSPATH_OVERRIDES = "lib/ignite-core-2.6.0.jar";
    private static final String HOST = "127.0.0.1";
    static final int PORT = 10800;
    private static final String CACHE_NAME = "thin-cache";
    private static UUID thinClientUuid;

    public static void main(String[] args) throws Exception {
        final int reportPort = Integer.parseInt(args[0]);
        final IgniteClient thinClient = startClient();
        thinClientUuid = UUID.randomUUID();
        sendReport(reportPort, Server.ReportType.START, thinClientUuid);

        LOG.info("Started thin client ver={}", IgniteProperties.get("ignite.version"));

        putValues(thinClient);
        queryValues(thinClient);

        sendReport(reportPort, Server.ReportType.TESTS_COMPLETE, thinClientUuid);

        Thread.sleep(60_000);
        System.exit(1);
    }

    private static void queryValues(IgniteClient thinClient) {
        LOG.info("Testing query");
        final ClientCache<String, String> clientCache = thinClient.getOrCreateCache(CACHE_NAME);
        List<Cache.Entry<Object, Object>> entries = clientCache.query(new ScanQuery<>()).getAll();
        assertEquals(2, entries.size());
    }

    private static void putValues(IgniteClient thinClient) {
        LOG.info("Testing put");
        final ClientCache<String, String> clientCache = thinClient.getOrCreateCache(CACHE_NAME);
        clientCache.put("Moscow", "095");
        clientCache.put("Vladimir", "033");
    }

    static IgniteClient startClient() {
        ClientConfiguration clientConfiguration = new ClientConfiguration().setAddresses(HOST + ":" + PORT);
        IgniteClient igniteClient = Ignition.startClient(clientConfiguration);

        return igniteClient;
    }
}
