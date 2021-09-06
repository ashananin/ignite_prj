import org.apache.ignite.Ignite;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SeparateJVM {
    static final int BASE_PORT = 48_000;
    static final int PORT_RANGE = 10;
    static final String DELIM = "\t";
    private static final Logger LOG = LoggerFactory.getLogger(Server.class);
    private static final long MAX_WAIT_MS = 30_000;

    static void sendReport(int port, final ReportType action, final UUID uuid) {
        final String output = action + DELIM + uuid;
        LOG.info("Reporting {} to port {}", output, port);
        try (Socket socket = new Socket("localhost", port)) {
            final OutputStream outputStream = socket.getOutputStream();
            outputStream.write((output + "\n").getBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void clusterNodeDownMovesServiceToNextNode() throws IOException, InterruptedException {
        final ServerSocket serverSocket = waitForPortRangeFree();
        final Server server = new Server(serverSocket);
        final int reportPort = serverSocket.getLocalPort();
        final int discoPort = serverSocket.getLocalPort() + 1;

        LOG.info("reportPort={} discoPort={}", reportPort, discoPort);

        List<String> args = Arrays.asList(String.valueOf(reportPort), String.valueOf(discoPort));

        final Process node1Process = JavaProcess.exec(Node.class, args);
        final Process node2Process = JavaProcess.exec(Node.class, args);
        final Process node3Process = JavaProcess.exec(Node.class, args);

        waitFor(() -> 3 == server.actionCount(ReportType.START));
        LOG.info("All nodes up");

        final Ignite ignite = Node.startNode("Client", true, discoPort, null);

        waitFor(() -> 1 == server.actionCount(ReportType.EXECUTE));
        stopNodeWithServiceOnIt(ignite, server.lastExecutionUuid());

        LOG.info("Waiting for 2nd exec");
        waitFor(() -> 2 == server.actionCount(ReportType.EXECUTE));
        stopNodeWithServiceOnIt(ignite, server.lastExecutionUuid());

        LOG.info("Waiting for 3rd exec");
        waitFor(() -> 3 == server.actionCount(ReportType.EXECUTE));
        stopNodeWithServiceOnIt(ignite, server.lastExecutionUuid());

        assertEquals(3, server.uniqueUuids(ReportType.EXECUTE).size());

        node1Process.destroyForcibly();
        node2Process.destroyForcibly();
        node3Process.destroyForcibly();
        serverSocket.close();
    }

    private static ServerSocket waitForPortRangeFree() throws IOException, InterruptedException {
        for (int port = BASE_PORT; port < BASE_PORT + PORT_RANGE; port++) {
            while (true)
                try (Socket socket = new Socket("localhost", port)) {
                    if (socket.isConnected()) {
                        LOG.info("Port {} still busy, waiting...", port);
                        Thread.sleep(100);
                    }
                } catch (ConnectException ce) {
                    LOG.info("Port {} is free", port);
                    break;
                }
        }
        return new ServerSocket(BASE_PORT);
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

    enum ReportType {START, INIT, EXECUTE, CANCEL}

    static class Server {
        private static final Logger LOG = LoggerFactory.getLogger(Server.class);
        private final List<String> reports = new CopyOnWriteArrayList<>();
        private final ServerSocket serverSocket;

        public Server(final ServerSocket port) {
            this.serverSocket = port;
            new Thread(this::listen).start();
        }

        private void listen() {
            try {
                while (true) {
                    final Socket socket = serverSocket.accept();
                    final String report = new BufferedReader(new InputStreamReader(socket.getInputStream())).readLine();
                    LOG.info("Reported {}", report);
                    reports.add(report);
                    if (reports.size() == 12) {
                        socket.close();
                        return;
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        long actionCount(final ReportType action) {
            return reports.stream().filter(r -> r.startsWith(action.name())).count();
        }

        Set<String> uniqueUuids(final ReportType action) {
            return reports.stream().filter(r -> r.startsWith(action.name())).map(this::extractUuid).collect(Collectors.toSet());
        }

        UUID lastExecutionUuid() {
            for (int i = reports.size() - 1; i >= 0; i--) {
                if (reports.get(i).startsWith(ReportType.EXECUTE.name()))
                    return UUID.fromString(extractUuid(reports.get(i)));
            }
            return null;
        }

        private String extractUuid(String r) {
            return r.substring(r.indexOf(DELIM) + DELIM.length());
        }
    }
}
