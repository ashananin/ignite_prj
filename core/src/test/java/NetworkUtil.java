import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.Server;

import java.io.IOException;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.Socket;

public class NetworkUtil {
    private static final Logger LOG = LoggerFactory.getLogger(Server.class);
    static final int BASE_PORT = 48_000;
    static final int PORT_RANGE = 10;
    public static ServerSocket waitForPortRangeFree() throws IOException, InterruptedException {
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
}
