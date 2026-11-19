import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Demonstração do ciclo de disparo do agendador (secção "Agendamento").
 *
 * As tarefas são mock e a "publicação" de um job limita-se a escrever uma linha no ecrã.
 *
 * O que a demonstração mostra:
 *  1. scheduledTime(k) = t0 + k * interval  -> instantes fixos, sem deriva;
 *  2. execId = taskId + "-" + scheduledTime -> determinístico;
 *     o taskId é o nome da réplica + um contador local (ex.: s1-7), único sem
 *     coordenação entre réplicas (fase 3);
 *  3. um tick atrasado dispara as ocorrências em atraso com o scheduledTime
 *     original (ciclo "enquanto" do pseudocódigo);
 *  4. TriggerNow cria uma ocorrência extra sem alterar o ciclo da tarefa.
 */
public class SchedulerMock {

    // ------------------------------------------------------------------ Task

    static class Task {
        final String id;
        final String type;
        final int intervalSeconds;
        final String inputDir;
        final long t0;      // instante de registo (segundos desde a epoch)
        long nextK = 1;     // índice da próxima ocorrência a disparar

        Task(String id, String type, int intervalSeconds, String inputDir, long t0) {
            this.id = id;
            this.type = type;
            this.intervalSeconds = intervalSeconds;
            this.inputDir = inputDir;
            this.t0 = t0;
        }

        long scheduledTime(long k) {
            return t0 + k * intervalSeconds;
        }
    }

    // ------------------------------------------------------------ Dispatcher

    /** No sistema real: publica em jobs.<tipo> e regista a execução como DISPATCHED. */
    interface Dispatcher {
        void publish(String execId, Task task, long scheduledTime);
    }

    /** Mock: escreve o job no ecrã, indicando o atraso face ao instante planeado. */
    static class PrintDispatcher implements Dispatcher {
        @Override
        public void publish(String execId, Task task, long scheduledTime) {
            long now = now();
            System.out.printf("%s  publish jobs.%-8s execId=%-18s scheduled=%s  atraso=%ds  input=%s%n",
                    fmt(now), task.type, execId, fmt(scheduledTime), now - scheduledTime, task.inputDir);
        }
    }

    // ------------------------------------------------------------- Scheduler

    private final Map<String, Task> tasks = new ConcurrentHashMap<>();
    private final Dispatcher dispatcher;
    private final String replicaName;   // nome desta réplica (na fase 3 vai corresponder ao nome no grupo Spread)
    private final AtomicInteger nextTaskId = new AtomicInteger(1);
    // uma só thread: os ticks e os TriggerNow não correm em paralelo
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();

    SchedulerMock(String replicaName, Dispatcher dispatcher) {
        this.replicaName = replicaName;
        this.dispatcher = dispatcher;
    }

    void start() {
        timer.scheduleAtFixedRate(this::tick, 0, 1, TimeUnit.SECONDS);
    }

    void stop() {
        timer.shutdownNow();
    }


    /** Ciclo de disparo: corresponde ao pseudocódigo da secção "Agendamento" do enunciado. */
    private void tick() {
        try {
            long now = now();
            for (Task t : tasks.values()) {
                long st;
                while ((st = t.scheduledTime(t.nextK)) <= now) {
                    fire(t, st);
                    t.nextK++;
                }
            }
        } catch (Exception e) {
            // uma exceção não apanhada cancelaria os ticks seguintes
            e.printStackTrace();
        }
    }

    /**
     * Simula a operação RegisterTask: a réplica que recebe o pedido atribui o id e fixa o t0.
     * O id é o nome da réplica + um contador local para que duas réplicas não gerem o mesmo id.
     */
    String registerTask(String type, int intervalSeconds, String inputDir) {
        String id = replicaName + "-" + nextTaskId.getAndIncrement();
        Task t = new Task(id, type, intervalSeconds, inputDir, now());
        tasks.put(id, t);
        System.out.printf("%s  RegisterTask %s (%s, de %d em %d s)%n",
                fmt(t.t0), id, type, intervalSeconds, intervalSeconds);
        return id;
    }

    /** Simula a operação TriggerNow do contrato: ocorrência extra; nextK não muda. */
    Future<String> triggerNow(String taskId) {
        return timer.submit(() -> {
            Task t = tasks.get(taskId);
            long now = now();
            System.out.printf("%s  TriggerNow %s%n", fmt(now), taskId);
            fire(t, now);
            return t.id + "-" + now;
        });
    }

    private void fire(Task t, long scheduledTime) {
        String execId = t.id + "-" + scheduledTime;   // determinístico
        dispatcher.publish(execId, t, scheduledTime);
    }

    /** Apenas para a demonstração: bloqueia a thread do timer, simulando um tick atrasado. */
    void simulateStall(int seconds) {
        timer.submit(() -> {
            System.out.printf("%s  --- agendador bloqueado durante %d s ---%n", fmt(now()), seconds);
            try {
                Thread.sleep(seconds * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            System.out.printf("%s  --- agendador retoma ---%n", fmt(now()));
        });
    }

    // ------------------------------------------------------------- utilitários

    private static final DateTimeFormatter HMS = DateTimeFormatter.ofPattern("HH:mm:ss");

    static long now() {
        return Instant.now().getEpochSecond();
    }

    static String fmt(long epochSeconds) {
        return LocalTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneId.systemDefault()).format(HMS);
    }

    // ------------------------------------------------------------------ main

    public static void main(String[] args) throws Exception {
        String replicaName = args.length > 0 ? args[0] : "s1";
        SchedulerMock scheduler = new SchedulerMock(replicaName, new PrintDispatcher());
        scheduler.start();

        // intervalos curtos para a demonstração correr em poucos segundos
        String archive = scheduler.registerTask("archive", 3, "/data/input/documents");
        String report = scheduler.registerTask("report", 5, "/data/input/sales");

        Thread.sleep(11_000);
        scheduler.triggerNow(report).get();        // ocorrência extra de report

        Thread.sleep(3_000);
        scheduler.simulateStall(7);                // as ocorrências em atraso saem
                                                   // todas no tick seguinte
        Thread.sleep(14_000);
        scheduler.stop();
        System.out.println("fim da demonstração (" + archive + ", " + report + ")");
    }
}
