package edu.unc.mapseq.executor.bergmips.vc;

import java.util.Timer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BergMIPsVariantCallingWorkflowExecutorService {

    private final Logger logger = LoggerFactory.getLogger(BergMIPsVariantCallingWorkflowExecutorService.class);

    private final Timer mainTimer = new Timer();

    private BergMIPsVariantCallingWorkflowExecutorTask task;

    private Long period = 5L;

    public BergMIPsVariantCallingWorkflowExecutorService() {
        super();
    }

    public void start() throws Exception {
        logger.info("ENTERING start()");
        long delay = 1 * 60 * 1000;
        mainTimer.scheduleAtFixedRate(task, delay, period * 60 * 1000);
    }

    public void stop() throws Exception {
        logger.info("ENTERING stop()");
        mainTimer.purge();
        mainTimer.cancel();
    }

    public BergMIPsVariantCallingWorkflowExecutorTask getTask() {
        return task;
    }

    public void setTask(BergMIPsVariantCallingWorkflowExecutorTask task) {
        this.task = task;
    }

    public Long getPeriod() {
        return period;
    }

    public void setPeriod(Long period) {
        this.period = period;
    }

}
