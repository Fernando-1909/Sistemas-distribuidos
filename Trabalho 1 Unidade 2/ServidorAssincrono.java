import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

public class ServidorAssincrono {
    public static void main(String[] args) throws Exception {
        // uso: java ServidorAssincrono [ip_desta_maquina] [threads_do_pool]
        String ip = args.length > 0 ? args[0] : "192.168.10.1";
        int threads = args.length > 1 ? Integer.parseInt(args[1]) : 4;

        System.setProperty("java.rmi.server.hostname", ip);

        ServicoAssincronoImpl impl = new ServicoAssincronoImpl(threads);
        ServicoAssincrono stub = (ServicoAssincrono) UnicastRemoteObject.exportObject(impl, 2100);

        Registry registro = LocateRegistry.createRegistry(2099);
        registro.rebind("ServicoRPCAsync", stub);

        Log.log("Servidor RPC assíncrono pronto em " + ip + ":2099 (pool de " + threads + " threads)");
    }
}
