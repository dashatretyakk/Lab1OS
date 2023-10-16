import java.util.concurrent.atomic.AtomicBoolean;

public class Result {
    private final ResultType type;
    private final int value;
    private final String message;
    private AtomicBoolean isCashed = new AtomicBoolean();

    public AtomicBoolean isCashed() {
        return isCashed;
    }

    public void setCashed(AtomicBoolean cashed) {
        isCashed = cashed;
    }

    public Result(ResultType type, int value) {
        this.type = type;
        this.value = value;
        this.message = null;
    }

    public Result(ResultType type, String message) {
        this.type = type;
        this.value = -1;
        this.message = message;
    }

    public ResultType getType() {
        return type;
    }

    public int getValue() {
        return value;
    }

    public String getMessage() {
        return message;
    }
}

