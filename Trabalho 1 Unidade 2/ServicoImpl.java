import java.rmi.RemoteException;
import java.util.Locale;
import java.util.concurrent.locks.ReentrantLock;

public class ServicoImpl implements ServicoRemoto {

    // Se "serializar" for true, uma trava global força uma chamada por vez,
    // simulando um servidor de uma thread só (útil para demonstrar o contraste).
    private final ReentrantLock trava = new ReentrantLock();
    private final boolean serializar;

    public ServicoImpl(boolean serializar) {
        this.serializar = serializar;
    }

    private void entrar() {
        if (serializar) trava.lock();
    }

    private void sair() {
        if (serializar) trava.unlock();
    }

    private String thread() {
        return "[" + Thread.currentThread().getName() + "]";
    }

    @Override
    public double calcular(double a, double b, String operacao) throws RemoteException {
        entrar();
        try {
            Log.log(thread() + " calcular(" + a + ", " + b + ", " + operacao + ")");
            switch (operacao) {
                case "+": return a + b;
                case "-": return a - b;
                case "*": return a * b;
                case "/":
                    if (b == 0) throw new RemoteException("divisão por zero");
                    return a / b;
                default:
                    throw new RemoteException("operação inválida: " + operacao);
            }
        } finally {
            sair();
        }
    }

    @Override
    public String processarDemorado(int segundos) throws RemoteException {
        entrar();
        try {
            Log.log(thread() + " iniciou processarDemorado(" + segundos + ")");
            try {
                Thread.sleep(segundos * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RemoteException("processamento interrompido", e);
            }
            Log.log(thread() + " terminou processarDemorado(" + segundos + ")");
            return "processamento de " + segundos + "s concluído";
        } finally {
            sair();
        }
    }

    @Override
    public String processarColecao(double[] valores) throws RemoteException {
        entrar();
        try {
            Log.log(thread() + " processarColecao(" + valores.length + " valores)");
            if (valores.length == 0) throw new RemoteException("coleção vazia");

            double soma = 0, min = valores[0], max = valores[0];
            for (double v : valores) {
                soma += v;
                min = Math.min(min, v);
                max = Math.max(max, v);
            }
            return String.format(Locale.US, "media=%.2f min=%.2f max=%.2f",
                    soma / valores.length, min, max);
        } finally {
            sair();
        }
    }
}
