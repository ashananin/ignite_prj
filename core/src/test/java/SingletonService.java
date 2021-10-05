import org.apache.ignite.Ignition;
import org.apache.ignite.services.ServiceContext;
import server.Server;

import java.util.UUID;

import static server.Server.sendReport;

public class SingletonService implements ISingletonService {
    static final String SERVICE_NAME = "SingletonService";

    private transient UUID uuid;
    private final int reportPort;

    public SingletonService(int reportPort) {
        this.reportPort = reportPort;
    }

    @Override
    public void init(ServiceContext ctx) {
        uuid = Ignition.localIgnite().cluster().localNode().id();
        sendReport(reportPort, Server.ReportType.INIT, uuid);
    }

    @Override
    public void execute(ServiceContext ctx) {
        sendReport(reportPort, Server.ReportType.EXECUTE, uuid);
        while (!ctx.isCancelled()) {
            Thread.yield();
        }
    }

    @Override
    public void cancel(ServiceContext ctx) {
        sendReport(reportPort, Server.ReportType.CANCEL, uuid);
    }
}
