
public class MemoryManager {

    public static final int TOTAL_MEMORY = 2048;

    private int usedMemory = 0;

    public synchronized boolean allocate(int memory) {
        if (memory <= 0 ||
                memory > TOTAL_MEMORY - usedMemory) {
            return false;
        }

        usedMemory += memory;
        return true;
    }

    public synchronized void release(int memory) {
        if (memory <= 0 || memory > usedMemory) {
            throw new IllegalArgumentException(
                    "Invalid memory release");
        }

        usedMemory -= memory;
        notifyAll();
    }

    public synchronized void allocateWhenAvailable(int memory)
            throws InterruptedException {

        if (memory <= 0 || memory > TOTAL_MEMORY) {
            throw new IllegalArgumentException(
                    "Invalid memory request");
        }

        while (memory > TOTAL_MEMORY - usedMemory) {
            wait();
        }

        usedMemory += memory;
    }

    public synchronized int getUsedMemory() {
        return usedMemory;
    }

    public synchronized double getUtilization() {
        return usedMemory * 100.0 / TOTAL_MEMORY;
    }
}
