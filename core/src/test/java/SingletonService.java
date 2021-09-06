import org.apache.ignite.Ignition;
import org.apache.ignite.services.ServiceContext;

import java.util.UUID;

public class SingletonService implements ISingletonService {
    static final String SERVICE_NAME = "SingletonService";

    private transient UUID uuid;
    private final int reportPort;

    public SingletonService(int reportPort) {
        this.reportPort = reportPort;
    }

    @Override
    public void init(ServiceContext ctx) throws Exception {
        uuid = Ignition.localIgnite().cluster().localNode().id();
        SeparateJVM.sendReport(reportPort, SeparateJVM.ReportType.INIT, uuid);
    }

    @Override
    public void execute(ServiceContext ctx) throws Exception {
        SeparateJVM.sendReport(reportPort, SeparateJVM.ReportType.EXECUTE, uuid);
        while (!ctx.isCancelled()) {
            Thread.yield();
        }
    }

    @Override
    public void cancel(ServiceContext ctx) {
        SeparateJVM.sendReport(reportPort, SeparateJVM.ReportType.CANCEL, uuid);
    }

}
