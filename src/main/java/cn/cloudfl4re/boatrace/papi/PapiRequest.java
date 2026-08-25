package cn.cloudfl4re.boatrace.papi;

public record PapiRequest(String trackId, int rank, Field field) {
    public enum Field {
        LINE,
        NAME,
        TIME
    }
}
