import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

public class Servidor {
    public static void main(String[] args) throws Exception {
        // uso: java Servidor [ip_desta_maquina] [single]
        String ip = args.length > 0 ? args[0] : "192.168.10.1";
        boolean serializar = args.length > 1 && args[1].equals("single");

        // endereço que vai dentro do stub entregue aos clientes
        System.setProperty("java.rmi.server.hostname", ip);

        ServicoImpl impl = new ServicoImpl(serializar);
        ServicoRemoto stub = (ServicoRemoto) UnicastRemoteObject.exportObject(impl, 1100);

        Registry registro = LocateRegistry.createRegistry(1099);
        registro.rebind("ServicoRPC", stub);

        Log.log("Servidor RPC sincrono pronto em " + ip + ":1099 (modo: "
                + (serializar ? "uma chamada por vez" : "multithread") + ")");
    }
}
