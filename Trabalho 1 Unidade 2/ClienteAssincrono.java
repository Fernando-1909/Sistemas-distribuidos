import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class ClienteAssincrono {
    public static void main(String[] args) throws Exception {
        // uso: java ClienteAssincrono [ip_servidor] [nome] [segundos]
        String host = args.length > 0 ? args[0] : "192.168.10.1";
        String nome = args.length > 1 ? args[1] : "CLIENTE";
        int segundos = args.length > 2 ? Integer.parseInt(args[2]) : 10;

        Registry registro = LocateRegistry.getRegistry(host, 2099);
        ServicoAssincrono servico = (ServicoAssincrono) registro.lookup("ServicoRPCAsync");

        Log.log(nome);
        long inicio = System.currentTimeMillis();

        // dispara a operação demorada e NÃO espera a resposta
        Log.log("Operação solicitada: processarDemorado(" + segundos + ")");
        String ticket = servico.submeter("demorado", String.valueOf(segundos));
        Log.log("Requisição assíncrona enviada (" + ticket + ")");
        Log.log("Cliente continuou sua execução");

        // trabalho local enquanto o servidor processa em background
        for (int i = 1; i <= 3; i++) {
            Thread.sleep(2000);
            Log.log("Executando atividade local " + i
                    + " (resultado pronto? " + servico.pronto(ticket) + ")");
        }

        // só agora o cliente bloqueia, se o resultado ainda não estiver pronto
        Log.log("Pedindo o resultado (equivale ao future.get())");
        String resultado = servico.obterResultado(ticket);
        Log.log("Resultado RPC disponível: " + resultado);

        long total = (System.currentTimeMillis() - inicio) / 1000;
        Log.log("Tempo total: " + total + " segundos");
    }
}
