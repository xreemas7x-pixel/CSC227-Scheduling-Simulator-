
import java.util.*;

public class Main {

    public static void main(String[] args)
            throws InterruptedException {

        Queue<PCB> jobQueue = new LinkedList<>();
        Queue<PCB> readyQueue = new LinkedList<>();

        MemoryManager memory = new MemoryManager();
        System.out.print(
        "Enable Medium-Term Scheduler? (true/false): ");

Scanner input = new Scanner(System.in);
boolean enabled = input.nextBoolean();

MediumTermScheduler mediumScheduler =
        new MediumTermScheduler(
                readyQueue, memory, enabled);

mediumScheduler.start();

        JobLoader loader = new JobLoader(jobQueue);
        loader.start();
        loader.join();

        System.out.println("\n--- Loading Completed ---");

        LongTermScheduler scheduler =
                new LongTermScheduler(
                        jobQueue, readyQueue, memory);

        scheduler.start();

        // Wait until the first process enters Ready Queue
        PCB finished = null;

        synchronized (readyQueue) {
            while (readyQueue.isEmpty()
                    && scheduler.isAlive()) {
                readyQueue.wait(100);
            }

            if (!readyQueue.isEmpty()) {
                finished = readyQueue.poll();
            }
        }

        // Simulate completion and release its memory
        if (finished != null) {
            finished.state = "TERMINATED";
            memory.release(finished.memoryRequired);

            System.out.println(
                    "\nTest: Process " + finished.processId
                    + " terminated and released memory");
        }

        // Give the scheduler time to continue
        Thread.sleep(200);
mediumScheduler.evaluateAt(0);
        scheduler.stopScheduler();
        scheduler.join();
        mediumScheduler.stopScheduler();
mediumScheduler.join();

        System.out.println("\n--- Final Ready Queue ---");

        synchronized (readyQueue) {
            for (PCB p : readyQueue) {
                System.out.println(
                        "P" + p.processId
                        + " | State: " + p.state
                        + " | Memory: "
                        + p.memoryRequired + " MB");
            }
        }

        System.out.println(
                "\nFinal Memory Used: "
                + memory.getUsedMemory()
                + "/2048 MB");

        System.out.println("\n--- Test Finished ---");
    }
}
