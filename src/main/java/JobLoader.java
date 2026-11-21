
import java.io.*;
import java.util.Queue;

public class JobLoader extends Thread {

    private final Queue<PCB> jobQueue;
    private volatile boolean loadingFinished = false;

    public JobLoader(Queue<PCB> jobQueue) {
        this.jobQueue = jobQueue;
    }

    @Override
    public void run() {

        try (BufferedReader reader =
                new BufferedReader(new FileReader("job.txt"))) {

            String line;
            int lineNumber = 0;

            while ((line = reader.readLine()) != null) {

                lineNumber++;

                if (line.trim().isEmpty()) {
                    continue;
                }

                String[] data = line.trim().split("[:;]");

                if (data.length != 4) {
                    throw new IllegalArgumentException(
                            "Invalid format at line " + lineNumber);
                }

                int id = Integer.parseInt(data[0].trim());
                int burst = Integer.parseInt(data[1].trim());
                int priority = Integer.parseInt(data[2].trim());
                int memory = Integer.parseInt(data[3].trim());

                if (id <= 0 || burst <= 0 ||
                        priority < 1 || priority > 30 ||
                        memory <= 0 ||
                        memory > MemoryManager.TOTAL_MEMORY) {

                    throw new IllegalArgumentException(
                            "Invalid process at line " + lineNumber);
                }

                PCB process =
                        new PCB(id, burst, priority, memory);

                synchronized (jobQueue) {
                    jobQueue.add(process);
                    jobQueue.notifyAll();
                }

                System.out.println(
                        "Loaded Process " + process.processId);
            }

        } catch (IOException | IllegalArgumentException e) {

            System.out.println(
                    "Error loading jobs: " + e.getMessage());

        } finally {

            loadingFinished = true;

            synchronized (jobQueue) {
                jobQueue.notifyAll();
            }

            System.out.println("Job loading finished.");
        }
    }

    public boolean isLoadingFinished() {
        return loadingFinished;
    }
}
