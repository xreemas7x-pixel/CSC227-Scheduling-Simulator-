
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * The ONE CPU-scheduling implementation used by both the CPU-only demo and
 * the memory-aware integrated application.
 *
 * SJF: non-preemptive; shortest burst first; ties use input arrival order.
 * RR: 5-ms quantum; unfinished jobs return to the back of READY.
 * Priority: non-preemptive; priority 1 is highest; waiting jobs age every 4 ms.
 * Starvation: before FIRST dispatch, READY wait > (N at READY entry) * 5 ms.
 *
 * This version supports long-term admission but not medium-term swapping.
 * The medium-term scheduler requires a separate shared-clock / queue protocol.
 */
public final class ShortTermScheduler {
    public static final int ROUND_ROBIN_QUANTUM = 5;
    public static final int AGING_INTERVAL = 4;
    private static final int STARVATION_FACTOR = 5;

    public enum Algorithm { SJF, ROUND_ROBIN, PRIORITY_WITH_AGING }

    private ShortTermScheduler() { }

    private static final class Entry {
        final PCB pcb;
        final int inputOrder;
        final int admittedAt;
        final int nAtAdmission;
        final int starvationLimit;
        int effectivePriority;
        int nextAgingTime;
        int readySince;
        int totalReadyWaiting;
        boolean firstDispatched;
        boolean starved;

        Entry(PCB pcb, int inputOrder, int admittedAt, int nAtAdmission) {
            this.pcb = pcb;
            this.inputOrder = inputOrder;
            this.admittedAt = admittedAt;
            this.nAtAdmission = nAtAdmission;
            this.starvationLimit = nAtAdmission * STARVATION_FACTOR;
            this.effectivePriority = pcb.priority;
            this.nextAgingTime = admittedAt + AGING_INTERVAL;
            this.readySince = admittedAt;
            pcb.state = "READY";
        }
    }

    public static final class GanttBlock {
        public final int processId;
        public final int start;
        public final int end;

        GanttBlock(int processId, int start, int end) {
            this.processId = processId;
            this.start = start;
            this.end = end;
        }
    }

    /** Complete results of one simulation (a separate run is used per algorithm). */
    public static final class Result {
        public final Algorithm algorithm;
        public final List<PCB> processes;
        public final List<GanttBlock> gantt;
        public final List<Integer> starvedProcessIds;
        public final List<String> agingEvents;
        public final double averageWaitingTime;
        public final double averageTurnaroundTime;
        private final List<Entry> admitted;
        private final boolean integrated;
        private final int finalMemoryUsed;

        Result(Algorithm algorithm, List<PCB> processes, List<GanttBlock> gantt,
               List<String> agingEvents, List<Entry> admitted, boolean integrated,
               int finalMemoryUsed) {
            this.algorithm = algorithm;
            this.processes = new ArrayList<>(processes);
            this.gantt = new ArrayList<>(gantt);
            this.agingEvents = new ArrayList<>(agingEvents);
            this.admitted = new ArrayList<>(admitted);
            this.integrated = integrated;
            this.finalMemoryUsed = finalMemoryUsed;
            this.starvedProcessIds = new ArrayList<>();
            for (Entry entry : admitted) {
                if (entry.starved) {
                    starvedProcessIds.add(entry.pcb.processId);
                }
            }
            long totalWaiting = 0;
            long totalTurnaround = 0;
            for (PCB p : processes) {
                totalWaiting += p.waitingTime;
                totalTurnaround += p.turnaroundTime;
            }
            this.averageWaitingTime = processes.isEmpty() ? 0.0
                    : totalWaiting / (double) processes.size();
            this.averageTurnaroundTime = processes.isEmpty() ? 0.0
                    : totalTurnaround / (double) processes.size();
        }

        public void print() {
            System.out.println("\n====================================================");
            System.out.println((integrated ? "INTEGRATED CPU ALGORITHM: " : "CPU Algorithm: ")
                    + algorithm);
            System.out.println("====================================================");
            System.out.println("Gantt Chart (start | process | end):");
            for (GanttBlock block : gantt) {
                System.out.printf("[%d | P%d | %d] ", block.start,
                        block.processId, block.end);
            }
            System.out.println("\n");
            System.out.println("Process | Burst | Start | Termination | Waiting | Turnaround");
            for (PCB p : processes) {
                System.out.printf(Locale.US,
                        "P%-7d | %-5d | %-5d | %-11d | %-7d | %d%n",
                        p.processId, p.burstTime, p.startTime,
                        p.terminationTime, p.waitingTime, p.turnaroundTime);
            }
            System.out.printf(Locale.US, "Average Waiting Time: %.2f ms%n",
                    averageWaitingTime);
            System.out.printf(Locale.US, "Average Turnaround Time: %.2f ms%n",
                    averageTurnaroundTime);
            if (integrated) {
                System.out.println("Note: Waiting is time in READY only; Turnaround includes "
                        + "the time in JOB queue since arrival at t=0.");
                boolean printedJobDelays = false;
                for (Entry e : admitted) {
                    int jobDelay = e.admittedAt - e.pcb.arrivalTime;
                    if (jobDelay > 0) {
                        if (!printedJobDelays) {
                            System.out.println("Job queue delays (excluded from Waiting Time):");
                            printedJobDelays = true;
                        }
                        System.out.printf("  P%d: %d ms%n", e.pcb.processId, jobDelay);
                    }
                }
            }
            if (algorithm == Algorithm.PRIORITY_WITH_AGING) {
                System.out.println("Aging events:");
                if (agingEvents.isEmpty()) {
                    System.out.println("  None");
                } else {
                    for (String event : agingEvents) {
                        System.out.println("  " + event);
                    }
                }
                System.out.println("Starvation (waited in READY > N * 5 ms):");
                if (starvedProcessIds.isEmpty()) {
                    System.out.println("  None");
                }
                for (Entry e : admitted) {
                    if (e.starved) {
                        System.out.printf("  P%d waited %d ms before first dispatch, "
                                + "N=%d, threshold=%d ms%n",
                                e.pcb.processId, e.pcb.startTime - e.admittedAt,
                                e.nAtAdmission, e.starvationLimit);
                    }
                }
            }
            if (integrated) {
                System.out.println("Completed: " + processes.size() + "/" + processes.size());
                System.out.println("Final Memory Used: " + finalMemoryUsed + " MB");
            }
        }
    }

    /**
     * CPU-only regression/demo mode: all processes enter READY in file order at
     * t=0; input PCB objects are copied, so the caller's objects stay unchanged.
     */
    public static Result run(List<PCB> input, Algorithm algorithm) {
        validate(input, algorithm);
        List<PCB> copies = new ArrayList<>();
        for (PCB p : input) {
            copies.add(new PCB(p.processId, p.burstTime, p.priority, p.memoryRequired));
        }
        Simulation sim = new Simulation(copies, algorithm, null, null, null, null);
        for (PCB p : copies) {
            sim.admit(p, 0);
        }
        try {
            return sim.execute();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("CPU-only simulation interrupted", ex);
        }
    }

    /**
     * Integrated mode: LongTermScheduler must already be running. Admissions
     * are taken from the REAL shared READY queue, and memory is freed only on
     * termination. Only call this method when medium-term swapping is disabled.
     */
    public static Result runIntegrated(List<PCB> allJobs, Algorithm algorithm,
            Queue<PCB> jobQueue, Queue<PCB> sharedReadyQueue,
            MemoryManager memory, LongTermScheduler longTerm)
            throws InterruptedException {
        validate(allJobs, algorithm);
        if (jobQueue == null || sharedReadyQueue == null || memory == null || longTerm == null) {
            throw new IllegalArgumentException("Integrated scheduler dependencies are required");
        }
        return new Simulation(allJobs, algorithm,
                jobQueue, sharedReadyQueue, memory, longTerm).execute();
    }

    private static void validate(List<PCB> processes, Algorithm algorithm) {
        if (processes == null || algorithm == null) {
            throw new IllegalArgumentException("Processes and algorithm are required");
        }
        Set<Integer> ids = new HashSet<>();
        for (PCB p : processes) {
            if (p == null || p.processId <= 0 || p.burstTime <= 0
                    || p.priority < 1 || p.priority > 30
                    || p.memoryRequired <= 0 || p.memoryRequired > MemoryManager.TOTAL_MEMORY
                    || !ids.add(p.processId)) {
                throw new IllegalArgumentException("Invalid or duplicate process");
            }
        }
    }

    private static final class Simulation {
        final List<PCB> processes;
        final Algorithm algorithm;
        final Queue<PCB> jobQueue;
        final Queue<PCB> sharedReadyQueue;
        final MemoryManager memory;
        final LongTermScheduler longTerm;
        final Map<Integer, Integer> order = new HashMap<>();
        final List<Entry> ready = new ArrayList<>();
        final List<Entry> admitted = new ArrayList<>();
        final List<GanttBlock> gantt = new ArrayList<>();
        final List<String> agingEvents = new ArrayList<>();
        int time;
        int completed;

        Simulation(List<PCB> processes, Algorithm algorithm, Queue<PCB> jobQueue,
                Queue<PCB> sharedReadyQueue, MemoryManager memory,
                LongTermScheduler longTerm) {
            this.processes = processes;
            this.algorithm = algorithm;
            this.jobQueue = jobQueue;
            this.sharedReadyQueue = sharedReadyQueue;
            this.memory = memory;
            this.longTerm = longTerm;
            for (int i = 0; i < processes.size(); i++) {
                order.put(processes.get(i).processId, i);
            }
        }

        void admit(PCB pcb, int atTime) {
            Entry e = new Entry(pcb, order.get(pcb.processId),
                    atTime, ready.size() + 1);
            ready.add(e);
            admitted.add(e);
            if (memory != null) {
                System.out.printf("[t=%d ms] P%d admitted to READY "
                        + "(N=%d, memory=%d MB)%n",
                        atTime, pcb.processId, e.nAtAdmission, pcb.memoryRequired);
            }
        }

        Result execute() throws InterruptedException {
            while (completed < processes.size()) {
                if (memory != null) {
                    admitAvailableJobs();
                }
                if (ready.isEmpty()) {
                    throw new IllegalStateException("No READY job available; check admission logic");
                }
                Entry current = select();
                ready.remove(current);
                PCB p = current.pcb;
                current.totalReadyWaiting += time - current.readySince;
                p.state = "RUNNING";
                if (!current.firstDispatched) {
                    current.firstDispatched = true;
                    p.startTime = time;
                    if (algorithm == Algorithm.PRIORITY_WITH_AGING
                            && time - current.admittedAt > current.starvationLimit) {
                        current.starved = true;
                    }
                }
                int start = time;
                int duration = algorithm == Algorithm.ROUND_ROBIN
                        ? Math.min(ROUND_ROBIN_QUANTUM, p.remainingTime)
                        : p.remainingTime;
                for (int step = 0; step < duration; step++) {
                    p.remainingTime--;
                    time++;
                    if (algorithm == Algorithm.PRIORITY_WITH_AGING) {
                        ageAndCheckStarvation();
                    }
                }
                gantt.add(new GanttBlock(p.processId, start, time));
                if (p.remainingTime == 0) {
                    p.state = "TERMINATED";
                    p.terminationTime = time;
                    p.turnaroundTime = time - p.arrivalTime;
                    // READY waiting only (exclude time in the JOB queue).
                    p.waitingTime = current.totalReadyWaiting;
                    if (memory != null) {
                        memory.release(p.memoryRequired);
                    }
                    completed++;
                } else {
                    p.state = "READY";
                    current.readySince = time;
                    ready.add(current);
                }
            }
            int remainingMemory = memory == null ? 0 : memory.getUsedMemory();
            return new Result(algorithm, processes, gantt, agingEvents,
                    admitted, memory != null, remainingMemory);
        }

        Entry select() {
            if (algorithm == Algorithm.ROUND_ROBIN) {
                return ready.get(0);
            }
            Comparator<Entry> comparator = algorithm == Algorithm.SJF
                    ? Comparator.comparingInt((Entry e) -> e.pcb.burstTime)
                            .thenComparingInt(e -> e.inputOrder)
                    : Comparator.comparingInt((Entry e) -> e.effectivePriority)
                            .thenComparingInt(e -> e.inputOrder);
            return ready.stream().min(comparator).orElseThrow();
        }

        void ageAndCheckStarvation() {
            for (Entry e : ready) {
                if (!e.firstDispatched && time - e.admittedAt > e.starvationLimit) {
                    e.starved = true;
                }
                if (time >= e.nextAgingTime) {
                    int old = e.effectivePriority;
                    e.effectivePriority = Math.max(1, old - 1);
                    e.nextAgingTime += AGING_INTERVAL;
                    if (e.effectivePriority != old) {
                        agingEvents.add("[t=" + time + " ms] P" + e.pcb.processId
                                + ": " + old + " -> " + e.effectivePriority);
                    }
                }
            }
        }

        /**
         * Barrier at a CPU allocation boundary: collect all currently admissible
         * jobs before making the next CPU decision, with zero simulated-time
         * cost. The producer runs on a real thread; the CPU clock is simulated.
         */
        void admitAvailableJobs() throws InterruptedException {
            while (true) {
                synchronized (sharedReadyQueue) {
                    while (!sharedReadyQueue.isEmpty()) {
                        admit(sharedReadyQueue.poll(), time);
                    }
                }
                int accountedMemory = 0;
                for (Entry e : ready) {
                    accountedMemory += e.pcb.memoryRequired;
                }
                int usedMemory = memory.getUsedMemory();
                PCB next;
                synchronized (jobQueue) {
                    next = jobQueue.peek();
                }
                boolean settled = accountedMemory == usedMemory
                        && (next == null || next.memoryRequired
                        > MemoryManager.TOTAL_MEMORY - usedMemory);
                if (settled) {
                    return;
                }
                if (!longTerm.isAlive() && accountedMemory == usedMemory) {
                    if (next != null) {
                        throw new IllegalStateException("Long-term scheduler ended with waiting jobs");
                    }
                    return;
                }
                synchronized (sharedReadyQueue) {
                    sharedReadyQueue.wait(2);
                }
            }
        }
    }
}
