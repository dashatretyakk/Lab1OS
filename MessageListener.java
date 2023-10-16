public interface MessageListener {
    void onMessage(String message);
    void onError(String error);
}
