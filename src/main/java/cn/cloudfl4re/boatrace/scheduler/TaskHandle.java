package cn.cloudfl4re.boatrace.scheduler;

@FunctionalInterface
public interface TaskHandle {
    TaskHandle NOOP = () -> {
    };

    void cancel();
}
