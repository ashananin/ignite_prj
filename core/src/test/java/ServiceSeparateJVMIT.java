import org.apache.ignite.Ignite;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.Server;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ServiceSeparateJVMIT {
    private static final Logger LOG = LoggerFactory.getLogger(Server.class);
    private static final long MAX_WAIT_MS = 30_000;

    @Test
    void clusterNodeDownMovesServiceToNextNode() throws IOException, InterruptedException {
        final ServerSocket serverSocket = NetworkUtil.waitForPortRangeFree();
        final Server server = new Server(serverSocket);
        final int reportPort = serverSocket.getLocalPort();
        final int discoPort = serverSocket.getLocalPort() + 1;

        LOG.info("reportPort={} discoPort={}", reportPort, discoPort);

        List<String> args = Arrays.asList(String.valueOf(reportPort), String.valueOf(discoPort));

        final Process node1Process = JavaProcess.exec(Node.class, args);
        final Process node2Process = JavaProcess.exec(Node.class, args);
        final Process node3Process = JavaProcess.exec(Node.class, args);

        waitFor(() -> 3 == server.actionCount(Server.ReportType.START));
        LOG.info("All nodes up");

        final Ignite ignite = Node.startNode("Client", true, discoPort, null);

        waitFor(() -> 1 == server.actionCount(Server.ReportType.EXECUTE));
        stopNodeWithServiceOnIt(ignite, server.lastExecutionUuid());

        LOG.info("Waiting for 2nd exec");
        waitFor(() -> 2 == server.actionCount(Server.ReportType.EXECUTE));
        stopNodeWithServiceOnIt(ignite, server.lastExecutionUuid());

        LOG.info("Waiting for 3rd exec");
        waitFor(() -> 3 == server.actionCount(Server.ReportType.EXECUTE));
        stopNodeWithServiceOnIt(ignite, server.lastExecutionUuid());

        assertEquals(3, server.uniqueUuids(Server.ReportType.EXECUTE).size());

        node1Process.destroyForcibly();
        node2Process.destroyForcibly();
        node3Process.destroyForcibly();
        serverSocket.close();
    }

    private static void stopNodeWithServiceOnIt(Ignite ignite, UUID uuid) {
        LOG.info("Stopping node {}", uuid);
        ignite.cluster().stopNodes(Collections.singleton(uuid));
    }

    private static void waitFor(final BooleanSupplier cond) throws InterruptedException {
        final long waitStart = System.currentTimeMillis();
        while (true) {
            if (cond.getAsBoolean()) return;
            if (System.currentTimeMillis() - waitStart > MAX_WAIT_MS) throw new AssertionError("Wait timed out");
            Thread.sleep(200);
        }
    }
}
