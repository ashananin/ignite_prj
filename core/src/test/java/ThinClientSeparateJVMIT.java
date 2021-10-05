import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.Server;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;


public class ThinClientSeparateJVMIT {
    private static final Logger LOG = LoggerFactory.getLogger(Server.class);
    private static final long MAX_WAIT_MS = 30_000;

    @Test
    void thinClientOldVersion() throws IOException, InterruptedException {
        final ServerSocket serverSocket = NetworkUtil.waitForPortRangeFree();
        final Server server = new Server(serverSocket);
        final int reportPort = serverSocket.getLocalPort();
        final int discoPort = serverSocket.getLocalPort() + 1;

        LOG.info("reportPort={} discoPort={}", reportPort, discoPort);

        List<String> args = Arrays.asList(String.valueOf(reportPort), String.valueOf(discoPort));

        final Process node1Process = JavaProcess.exec(Node.class, args);

        waitFor(() -> 1 == server.actionCount(Server.ReportType.START));
        LOG.info("Node up");

        final Process thinClientProcess = JavaProcess.exec(ThinClient.CLASSPATH_OVERRIDES, ThinClient.class, args);

        waitFor(() -> 1 == server.actionCount(Server.ReportType.TESTS_COMPLETE));

        LOG.info("All tests complete");

        thinClientProcess.destroyForcibly();
        node1Process.destroyForcibly();
        serverSocket.close();
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
