import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ServicoRemoto extends Remote {

    // operação rápida: recebe dois números e uma operação (+, -, *, /)
    double calcular(double a, double b, String operacao) throws RemoteException;

    // operação demorada: fica "processando" durante N segundos
    String processarDemorado(int segundos) throws RemoteException;

    // processamento de coleção: média, mínimo e máximo
    String processarColecao(double[] valores) throws RemoteException;
}
