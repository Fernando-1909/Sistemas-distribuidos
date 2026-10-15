import java.rmi.RemoteException;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

public class ServicoAssincronoImpl implements ServicoAssincrono {

    // reaproveita a lógica das operações do servidor síncrono (sem trava)
    private final ServicoImpl servico = new ServicoImpl(false);

    // pool de workers: é ele que executa as tarefas em paralelo
    private final ExecutorService pool;
    private final Map<String, Future<String>> tarefas = new ConcurrentHashMap<>();
    private final AtomicInteger contador = new AtomicInteger();

    public ServicoAssincronoImpl(int threads) {
        this.pool = Executors.newFixedThreadPool(threads);
    }

    @Override
    public String submeter(String operacao, String argumentos) throws RemoteException {
        String ticket = "tarefa-" + contador.incrementAndGet();
        Log.log("[" + Thread.currentThread().getName() + "] recebeu " + operacao
                + "(" + argumentos + ") -> " + ticket + " (enfileirada)");
        tarefas.put(ticket, pool.submit(() -> executar(operacao, argumentos)));
        return ticket; // retorna imediatamente, sem esperar o processamento
    }

    @Override
    public boolean pronto(String ticket) throws RemoteException {
        return buscar(ticket).isDone();
    }

    @Override
    public String obterResultado(String ticket) throws RemoteException {
        try {
            return buscar(ticket).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RemoteException("espera interrompida", e);
        } catch (ExecutionException e) {
            throw new RemoteException("falha na tarefa: " + e.getCause().getMessage(), e.getCause());
        }
    }

    private Future<String> buscar(String ticket) throws RemoteException {
        Future<String> f = tarefas.get(ticket);
        if (f == null) throw new RemoteException("ticket inexistente: " + ticket);
        return f;
    }

    // roda dentro de uma thread do pool
    private String executar(String operacao, String argumentos) throws Exception {
        String[] p = argumentos.split(",");
        switch (operacao) {
            case "calcular":
                return String.valueOf(servico.calcular(
                        Double.parseDouble(p[0].trim()), Double.parseDouble(p[1].trim()), p[2].trim()));
            case "demorado":
                return servico.processarDemorado(Integer.parseInt(p[0].trim()));
            case "colecao":
                double[] valores = Arrays.stream(p).mapToDouble(s -> Double.parseDouble(s.trim())).toArray();
                return servico.processarColecao(valores);
            default:
                throw new IllegalArgumentException("operação desconhecida: " + operacao);
        }
    }
}
