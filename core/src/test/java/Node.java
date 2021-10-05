import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.configuration.ClientConnectorConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.internal.IgniteProperties;
import org.apache.ignite.services.ServiceConfiguration;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.Server;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.UUID;

import static server.Server.sendReport;

public class Node {
    private static final Logger LOG = LoggerFactory.getLogger(Node.class);

    public static void main(String[] args) throws IOException, InterruptedException {
        final int reportPort = Integer.parseInt(args[0]);
        final int discoPort = Integer.parseInt(args[1]);

        final Ignite ignite = startNode("Node", false, discoPort, new SingletonService(reportPort));
        final UUID uuid = ignite.cluster().localNode().id();
        sendReport(reportPort, Server.ReportType.START, uuid);
        LOG.info("Started node ver={}", IgniteProperties.get("ignite.version"));

        Thread.sleep(60_000);
        System.exit(1);
    }

    static Ignite startNode(final String nodePrefix, final boolean clientMode, int discoPort, SingletonService service) throws IOException {
        final IgniteConfiguration igniteConfiguration = new IgniteConfiguration();
        igniteConfiguration.setDiscoverySpi(new TcpDiscoverySpi()
                .setIpFinder(new TcpDiscoveryVmIpFinder().setAddresses(Collections.singleton("localhost:" + discoPort + ".." + (discoPort + NetworkUtil.PORT_RANGE - 2))))
                .setLocalPort(discoPort)
                .setLocalPortRange(NetworkUtil.PORT_RANGE - 2));
        igniteConfiguration.setClientConnectorConfiguration(new ClientConnectorConfiguration().setThinClientEnabled(true).setPort(ThinClient.PORT));
        if (service != null) {
            igniteConfiguration.setServiceConfiguration(new ServiceConfiguration()
                    .setName(SingletonService.SERVICE_NAME)
                    .setService(service)
                    .setTotalCount(1)
                    .setMaxPerNodeCount(1));
        }
        final String workDir = Files.createTempDirectory(nodePrefix).toAbsolutePath().toString();
        LOG.info("Working dir: {}", workDir);
        igniteConfiguration.setWorkDirectory(workDir);
        igniteConfiguration.setClientMode(clientMode);
        return Ignition.getOrStart(igniteConfiguration);
    }
}
