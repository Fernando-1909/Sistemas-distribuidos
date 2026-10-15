import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class ClienteSincrono {
    public static void main(String[] args) throws Exception {
        // uso: java ClienteSincrono [ip_servidor] [nome] [segundos]
        String host = args.length > 0 ? args[0] : "192.168.10.1";
        String nome = args.length > 1 ? args[1] : "CLIENTE";
        int segundos = args.length > 2 ? Integer.parseInt(args[2]) : 10;

        Registry registro = LocateRegistry.getRegistry(host, 1099);
        ServicoRemoto servico = (ServicoRemoto) registro.lookup("ServicoRPC");

        Log.log(nome);

        // 1) operação rápida
        Log.log("Enviando requisição RPC");
        Log.log("Operação solicitada: calcular(12, 5, *)");
        double resultado = servico.calcular(12, 5, "*");
        Log.log("Resposta recebida: " + resultado);

        // 2) processamento de coleção
        double[] temperaturas = {22.5, 31.0, 28.4, 35.2, 19.8};
        Log.log("Enviando requisição RPC");
        Log.log("Operação solicitada: processarColecao(" + temperaturas.length + " valores)");
        String estatisticas = servico.processarColecao(temperaturas);
        Log.log("Resposta recebida: " + estatisticas);

        // 3) operação demorada: o cliente fica BLOQUEADO até a resposta chegar
        long inicio = System.currentTimeMillis();
        Log.log("Enviando requisição RPC");
        Log.log("Operação solicitada: processarDemorado(" + segundos + ")");
        String resposta = servico.processarDemorado(segundos);
        Log.log("Resposta recebida: " + resposta);
        long total = (System.currentTimeMillis() - inicio) / 1000;
        Log.log("Tempo total: " + total + " segundos");
    }
}
