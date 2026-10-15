import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ServicoAssincrono extends Remote {

    // Enfileira a tarefa e devolve NA HORA um ticket; o processamento segue em background.
    // operacao: "calcular" | "demorado" | "colecao"
    // argumentos: valores separados por vírgula (ex.: "12,5,*" | "10" | "22.5,31.0,28.4")
    String submeter(String operacao, String argumentos) throws RemoteException;

    // consulta sem bloquear: a tarefa já terminou?
    boolean pronto(String ticket) throws RemoteException;

    // bloqueia até a tarefa terminar e devolve o resultado (equivale ao future.get())
    String obterResultado(String ticket) throws RemoteException;
}
