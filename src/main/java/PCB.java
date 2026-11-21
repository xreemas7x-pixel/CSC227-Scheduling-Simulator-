
public class PCB {
    int processId;
    int burstTime;
    int remainingTime;
    int priority;
    int memoryRequired;

    String state;
    int arrivalTime;
    int startTime;
    int terminationTime;
    int waitingTime;
    int turnaroundTime;

    public PCB(int id, int burst, int priority, int memory) {
        this.processId = id;
        this.burstTime = burst;
        this.remainingTime = burst;
        this.priority = priority;
        this.memoryRequired = memory;

        this.state = "NEW";
        this.arrivalTime = 0;
        this.startTime = -1;
        this.terminationTime = -1;
        this.waitingTime = 0;
        this.turnaroundTime = 0;
    }
}
