import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.services.ServiceConfiguration;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.UUID;

public class Node {
    private static final Logger LOG = LoggerFactory.getLogger(Node.class);

    public static void main(String[] args) throws IOException, InterruptedException {
        final int reportPort = Integer.parseInt(args[0]);
        final int discoPort = Integer.parseInt(args[1]);

        final Ignite ignite = startNode("Node", false, discoPort, new SingletonService(reportPort));
        final UUID uuid = ignite.cluster().localNode().id();
        SeparateJVM.sendReport(reportPort, SeparateJVM.ReportType.START, uuid);

        Thread.sleep(60_000);
        System.exit(1);
    }

    static Ignite startNode(final String nodePrefix, final boolean clientMode, int discoPort, SingletonService service) throws IOException {
        final IgniteConfiguration igniteConfiguration = new IgniteConfiguration();
        igniteConfiguration.setDiscoverySpi(new TcpDiscoverySpi()
                .setIpFinder(new TcpDiscoveryVmIpFinder().setAddresses(Collections.singleton("localhost:" + discoPort + ".." + (discoPort + SeparateJVM.PORT_RANGE - 2))))
                .setLocalPort(discoPort)
                .setLocalPortRange(SeparateJVM.PORT_RANGE - 2));
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
