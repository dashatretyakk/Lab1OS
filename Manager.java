import java.io.File;
import java.io.FileNotFoundException;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class Manager {
    private MessageListener messageListener;

    private ExecutorService executor = Executors.newFixedThreadPool(3);
    private ScheduledExecutorService timeoutExecutor = Executors.newScheduledThreadPool(1);

    private final ConcurrentHashMap<Integer, CompletableFuture<Result>> combineResultsMemoization = new ConcurrentHashMap<>();

    private final AtomicInteger failedFComputations = new AtomicInteger(0);
    private final AtomicInteger failedGComputations = new AtomicInteger(0);
    private final AtomicInteger totalComputed = new AtomicInteger(0);
    private final AtomicInteger totalFailed = new AtomicInteger(0);
    private final AtomicBoolean outputPaused = new AtomicBoolean(false);

    // Починає обчислення даних, зчитаних з файлу "input.txt".
    public void startComputations() {
        Thread computationThread = new Thread(() -> {
            try {
                Scanner fileScanner = new Scanner(new File("input.txt"));
                while (fileScanner.hasNextInt()) {
                    int x = fileScanner.nextInt();
                    CompletableFuture<Result> result;
                    try {
                        result = combineResults(x);
                    } catch (RejectedExecutionException e) {
                        return;
                    }
                    Result finalResult = result.join();

                    // Інкрементуємо загальний лічильник
                    totalComputed.incrementAndGet();
                    if (finalResult.getType() == ResultType.FAILURE) {
                        totalFailed.incrementAndGet();
                    }

                    Thread.sleep(0);

                    // Вивід результатів
                    String cashFlag = finalResult.isCashed().get() ? " (кеш)" : "";
                    if (finalResult.getType() == ResultType.SUCCESS) {
                        safePrintln("Результат для x = " + x + ": " + finalResult.getValue() + cashFlag);
                    } else {
                        safeErrorPrintln("Помилка для x = " + x + ": " + finalResult.getMessage() + cashFlag);
                    }
                }
                fileScanner.close();
            } catch (FileNotFoundException e) {
                safeErrorPrintln("Файл не знайдено: " + e.getMessage());
            } catch (InterruptedException e) {
                safeErrorPrintln("Обчислення перервані");
            }

            executor.shutdown();
            timeoutExecutor.shutdown();

        });
        computationThread.start();
    }

    // Комбінує результати обчислень для параметра x.
    private CompletableFuture<Result> combineResults(int x) {
        CompletableFuture<Result> cachedResult = combineResultsMemoization.get(x);
        if (cachedResult != null) {
            return cachedResult.thenApply(result -> {
                // Встановлюємо прапорець isCashed тільки для результатів з кешу (для демонстрації роботи меморізації)
                result.setCashed(new AtomicBoolean(true));
                return result;
            });
        } else {
            CompletableFuture<Result> newResult = CompletableFuture.supplyAsync(() -> {
                Future<Result> futureF = executor.submit(() -> computeF(x));
                Future<Result> futureG = executor.submit(() -> computeG(x));

                try {
                    Result resultF;
                    try {
                        resultF = futureF.get(1, TimeUnit.SECONDS);
                        checkErrorThreshold();

                        if (resultF.getType() == ResultType.FAILURE) {
                            futureG.cancel(true);  // Відміна виконання G
                            return resultF;
                        }
                    } catch (TimeoutException e) {
                        futureF.cancel(true);  // Відміна виконання F
                        futureG.cancel(true);  // Відміна виконання G
                        return new Result(ResultType.FAILURE, "Перевищено час виконання для F");
                    } catch (Exception ex) {
                        futureG.cancel(true);  // Відміна виконання G
                        handleFailure(ex);
                        return new Result(ResultType.FAILURE, ex.getMessage());
                    }

                    Result resultG;
                    try {
                        resultG = futureG.get(1, TimeUnit.SECONDS);
                        checkErrorThreshold();

                        if (resultG.getType() == ResultType.FAILURE) {
                            futureF.cancel(true);  // Відміна виконання F
                            return resultG;
                        }
                    } catch (TimeoutException e) {
                        futureG.cancel(true);  // Відміна виконання G
                        futureF.cancel(true);  // Відміна виконання F
                        return new Result(ResultType.FAILURE, "Перевищено час виконання для G");
                    } catch (Exception ex) {
                        handleFailure(ex);
                        return new Result(ResultType.FAILURE, ex.getMessage());
                    }

                    return new Result(ResultType.SUCCESS, resultF.getValue() + resultG.getValue());
                } catch (Exception ex) {
                    handleFailure(ex);
                    return new Result(ResultType.FAILURE, ex.getMessage());
                }
            }, executor);
            combineResultsMemoization.put(x, newResult);
            return newResult;
        }
    }

    // Імітує обчислення функції F для заданого параметра x.
    private Result computeF(int x) {
        Random random = new Random();
        int result = random.nextInt(10);
        try {
            // Імітація перевищення таймауту для 20% випадків
            if (random.nextInt(5) == 0) Thread.sleep(1500);
            else Thread.sleep(100);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        int errorChance = 3; //вірогідність помилки 30%
        safePrintln("F для x = " + x + ": " + result + (result < errorChance ? " (Помилка)" : ""));
        // Імітація помилки обчислень
        if (result < errorChance) {
            failedFComputations.incrementAndGet();
            return new Result(ResultType.FAILURE, "Помилка обчислення f(x) для x = " + x);
        }
        return new Result(ResultType.SUCCESS, result);
    }

    // Імітує обчислення функції G для заданого параметра x.
    private Result computeG(int x) {
        Random random = new Random();
        int result = random.nextInt(10);

        try {
            // Імітація перевищення таймауту для 20% випадків
            if (random.nextInt(5) == 0) Thread.sleep(1500);
            else Thread.sleep(300);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        int errorChance = 3; //вірогідність помилки 30%
        safePrintln("G для x = " + x + ": " + result + (result < errorChance ? " (Помилка)" : ""));
        // Імітація помилки обчислень
        if (result < errorChance) {
            failedGComputations.incrementAndGet();
            return new Result(ResultType.FAILURE, "Помилка обчислення g(x) для x = " + x);
        }
        return new Result(ResultType.SUCCESS, result);
    }

    // Завершення програми при перевищенні порога помилок в 50% однією з функцій
    private void checkErrorThreshold() {
        if (totalComputed.get() > 10 && (failedFComputations.get() * 2 > totalComputed.get() || failedGComputations.get() * 2 > totalComputed.get())) {
            safeErrorPrintln("Перевищено поріг помилок в обчисленнях!");
            executor.shutdownNow();
            timeoutExecutor.shutdownNow();
            System.exit(1);
        }
    }

    private void handleFailure(Throwable ex) {
        System.err.println("Сталася помилка: " + ex.getMessage());
    }

    public int getTotalComputed() {
        return totalComputed.get();
    }

    public int getTotalFailed() {
        return totalFailed.get();
    }

    public void forceCancel() {
        outputPaused.set(true);
        executor.shutdownNow();
        timeoutExecutor.shutdownNow();
    }

    public void pauseOutput() {
        outputPaused.set(true);
    }

    public void resumeOutput() {
        outputPaused.set(false);
    }

    public void reset() {
        // Скидаємо глобальні атомарні змінні до їхніх початкових значень
        failedFComputations.set(0);
        failedGComputations.set(0);
        totalComputed.set(0);
        totalFailed.set(0);
        outputPaused.set(false);

        combineResultsMemoization.clear();

        // Перетворимо executor-и, так як вони були завершені в методі forceCancel()
        executor = Executors.newFixedThreadPool(3);
        timeoutExecutor = Executors.newScheduledThreadPool(1);
    }

    // Додатковий метод для безпечного виведення
    private void safePrintln(String message) {
        if (!outputPaused.get() && messageListener != null) {
            messageListener.onMessage(message);
        }
    }


    // Додатковий метод для безпечного виведення помилок
    private void safeErrorPrintln(String message) {
        if (!outputPaused.get() && messageListener != null) {
            messageListener.onError(message);
        }
    }

    // Встановлює слухача повідомлень.
    public void setMessageListener(MessageListener messageListener) {
        this.messageListener = messageListener;
    }
}
