

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Scanner;

/**
 * Program entry point for CPU scheduling with long-term memory admission.
 * Scheduling rules are implemented ONLY in ShortTermScheduler.java.
 * Medium-term swapping is intentionally disabled until its team component
 * can be integrated with the shared simulated clock and queue states.
 */
public final class IntegratedMain {
    private IntegratedMain() { }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("================================");
        System.out.println("   CPU SCHEDULING SIMULATOR");
        System.out.println("================================");
        System.out.println("1. Shortest Job First (SJF)");
        System.out.println("2. Round Robin (5 ms)");
        System.out.println("3. Priority with Aging");
        System.out.println("4. Run All Algorithms");
        System.out.print("Choose an option (1-4): ");

        int choice;
        if (args.length > 0) {
            try {
                choice = Integer.parseInt(args[0]);
            } catch (NumberFormatException ex) {
                System.out.println("Invalid option. Enter 1, 2, 3 or 4.");
                return;
            }
        } else {
            Scanner scanner = new Scanner(System.in);
            choice = scanner.hasNextInt() ? scanner.nextInt() : 4;
        }
        switch (choice) {
            case 1 -> runOne(ShortTermScheduler.Algorithm.SJF);
            case 2 -> runOne(ShortTermScheduler.Algorithm.ROUND_ROBIN);
            case 3 -> runOne(ShortTermScheduler.Algorithm.PRIORITY_WITH_AGING);
            case 4 -> {
                for (ShortTermScheduler.Algorithm algorithm
                        : ShortTermScheduler.Algorithm.values()) {
                    runOne(algorithm);
                }
            }
            default -> {
                System.out.println("Invalid option. Enter 1, 2, 3 or 4.");
                return;
            }
        }
        System.out.println("\nSIMULATION COMPLETED");
    }

    private static void runOne(ShortTermScheduler.Algorithm algorithm)
            throws InterruptedException {
        Queue<PCB> jobQueue = new LinkedList<>();
        Queue<PCB> sharedReadyQueue = new LinkedList<>();
        MemoryManager memory = new MemoryManager();

        // Each algorithm gets brand-new queues, PCBs and memory state.
        JobLoader loader = new JobLoader(jobQueue);
        loader.start();
        loader.join();
        List<PCB> allJobs = new ArrayList<>(jobQueue);
        if (allJobs.isEmpty()) {
            System.out.println("No jobs loaded. Check job.txt in the project root.");
            return;
        }

        LongTermScheduler longTerm = new LongTermScheduler(
                jobQueue, sharedReadyQueue, memory);
        longTerm.start();
        try {
            ShortTermScheduler.Result result = ShortTermScheduler.runIntegrated(
                    allJobs, algorithm, jobQueue, sharedReadyQueue, memory, longTerm);
            longTerm.join();
            result.print();
        } finally {
            if (longTerm.isAlive()) {
                longTerm.stopScheduler();
                longTerm.join();
            }
        }
    }
}
