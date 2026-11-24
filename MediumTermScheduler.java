import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;

public class MediumTermScheduler extends Thread {

    private final Queue<PCB> readyQueue;
    private final Queue<PCB> suspendQueue = new LinkedList<>();
    private final MemoryManager memoryManager;
    private final boolean enabled;

    // The main thread sends simulation times to this thread.
    private final BlockingQueue<Integer> ticks =
            new LinkedBlockingQueue<>();

    private final Semaphore completed = new Semaphore(0);

    public MediumTermScheduler(
            Queue<PCB> readyQueue,
            MemoryManager memoryManager,
            boolean enabled) {

        this.readyQueue = readyQueue;
        this.memoryManager = memoryManager;
        this.enabled = enabled;
    }

    @Override
    public void run() {
        try {
            while (!isInterrupted()) {
                int time = ticks.take();

                try {
                    if (enabled) {
                        checkMemory(time);
                    }
                } finally {
                    completed.release();
                }
            }
        } catch (InterruptedException e) {
            interrupt();
        }
    }

    // Call once at each simulation time, from the main thread.
    // Returns after this thread finishes checking that time.
    public void evaluateAt(int time)
            throws InterruptedException {

        ticks.put(time);
        completed.acquire();
    }

    private void checkMemory(int time) {

        synchronized (readyQueue) {
            synchronized (memoryManager) {

                double before = memoryManager.getUtilization();

                if (before > 90.0) {
                    swapOut(time, before);

                } else if (before < 70.0) {
                    swapIn(time, before);
                }
            }
        }
    }

    private void swapOut(int time, double before) {

        PCB victim = null;

        for (PCB process : readyQueue) {
            if (victim == null ||
                    process.memoryRequired >
                    victim.memoryRequired) {

                victim = process;
            }
        }

        if (victim == null) {
            return;
        }

        String previousState = victim.state;

        readyQueue.remove(victim);
        memoryManager.release(victim.memoryRequired);

        victim.state = "SUSPENDED";
        suspendQueue.add(victim);

        printIntervention(
                time, victim, "Swap out",
                previousState, before);
    }

    private void swapIn(int time, double before) {

        PCB process = suspendQueue.peek();

        if (process == null) {
            return;
        }

        if (!memoryManager.allocate(process.memoryRequired)) {
            return;
        }

        String previousState = process.state;

        suspendQueue.poll();
        process.state = "READY";

        readyQueue.add(process);
        readyQueue.notifyAll();

        printIntervention(
                time, process, "Swap in",
                previousState, before);
    }

    private void printIntervention(
            int time,
            PCB process,
            String action,
            String previousState,
            double before) {

        System.out.println("\n[Time " + time + " ms]");
        System.out.println(
                "Medium-Term Scheduler Intervention");

        System.out.printf(
                "Memory utilization: %.2f%%%n", before);

        System.out.println(
                "Action: " + action
                + " Process " + process.processId);

        System.out.println(
                "Process " + process.processId
                + ": " + previousState
                + " -> " + process.state);

        System.out.printf(
                "Memory utilization after intervention: %.2f%%%n",
                memoryManager.getUtilization());
    }

    public void stopScheduler() {
        interrupt();
    }
}