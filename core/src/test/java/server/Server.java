package server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class Server {
    private static final Logger LOG = LoggerFactory.getLogger(Server.class);
    static final String DELIM = "\t";
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

    public long actionCount(final ReportType action) {
        return reports.stream().filter(r -> r.startsWith(action.name())).count();
    }

    public Set<String> uniqueUuids(final ReportType action) {
        return reports.stream().filter(r -> r.startsWith(action.name())).map(this::extractUuid).collect(Collectors.toSet());
    }

    public UUID lastExecutionUuid() {
        for (int i = reports.size() - 1; i >= 0; i--) {
            if (reports.get(i).startsWith(ReportType.EXECUTE.name()))
                return UUID.fromString(extractUuid(reports.get(i)));
        }
        return null;
    }

    private String extractUuid(String r) {
        return r.substring(r.indexOf(DELIM) + DELIM.length());
    }

    public enum ReportType {START, INIT, EXECUTE, CANCEL, TESTS_COMPLETE}

    public static void sendReport(int port, final Server.ReportType action, final UUID uuid) {
        final String output = action + DELIM + uuid;
        LOG.info("Reporting {} to port {}", output, port);
        try (Socket socket = new Socket("localhost", port)) {
            final OutputStream outputStream = socket.getOutputStream();
            outputStream.write((output + "\n").getBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
