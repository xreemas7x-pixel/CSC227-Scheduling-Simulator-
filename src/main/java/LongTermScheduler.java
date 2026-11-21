
import java.util.Queue;

public class LongTermScheduler extends Thread {

    private final Queue<PCB> jobQueue;
    private final Queue<PCB> readyQueue;
    private final MemoryManager memoryManager;

    private volatile boolean running = true;

    public LongTermScheduler(
            Queue<PCB> jobQueue,
            Queue<PCB> readyQueue,
            MemoryManager memoryManager) {

        this.jobQueue = jobQueue;
        this.readyQueue = readyQueue;
        this.memoryManager = memoryManager;
    }

    @Override
    public void run() {

        while (running && !isInterrupted()) {

            PCB process;

            synchronized (jobQueue) {
                process = jobQueue.peek();
            }

            if (process == null) {
                break;
            }

            try {
                System.out.println(
                        "Checking Process " + process.processId);

                // Wait until enough memory is available.
                memoryManager.allocateWhenAvailable(
                        process.memoryRequired);

                // Stop safely if shutdown was requested.
                if (!running || isInterrupted()) {
                    memoryManager.release(
                            process.memoryRequired);
                    break;
                }

                synchronized (jobQueue) {
                    jobQueue.poll();
                }

                synchronized (readyQueue) {
                    process.state = "READY";
                    readyQueue.add(process);
                    readyQueue.notifyAll();
                }

                System.out.println(
                        "Process " + process.processId
                        + " moved to Ready Queue");

                System.out.println(
                        "Memory Used: "
                        + memoryManager.getUsedMemory()
                        + "/2048 MB");

            } catch (InterruptedException e) {
                interrupt();
                break;
            }
        }
    }

    public void stopScheduler() {
        running = false;
        interrupt();
    }
}
