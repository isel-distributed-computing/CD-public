package demospread;

import spread.AdvancedMessageListener;
import spread.BasicMessageListener;
import spread.SpreadMessage;

/**
 * Embrulha um listener do Spread para que nenhuma exceção saia dos callbacks.
 *
 * A thread de receção do Spread (SpreadConnection.Listener) chama os callbacks
 * diretamente e só apanha SpreadException. Uma RuntimeException lançada num
 * callback termina essa thread: o membro continua ligado ao daemon, mas deixa
 * de receber mensagens e notificações de membership, sem qualquer aviso.
 *
 * Uso:
 *   BasicMessageListener l = SafeListener.of(new MessageHandling(...));
 *   connection.add(l);
 *   ...
 *   connection.remove(l);   // remover o mesmo objeto que foi adicionado
 */
public final class SafeListener {

    private SafeListener() { }

    public static BasicMessageListener of(BasicMessageListener inner) {
        return new BasicMessageListener() {
            @Override
            public void messageReceived(SpreadMessage msg) {
                try {
                    inner.messageReceived(msg);
                } catch (Exception e) {
                    report("messageReceived", e);
                }
            }
        };
    }

    public static AdvancedMessageListener of(AdvancedMessageListener inner) {
        return new AdvancedMessageListener() {
            @Override
            public void regularMessageReceived(SpreadMessage msg) {
                try {
                    inner.regularMessageReceived(msg);
                } catch (Exception e) {
                    report("regularMessageReceived", e);
                }
            }

            @Override
            public void membershipMessageReceived(SpreadMessage msg) {
                try {
                    inner.membershipMessageReceived(msg);
                } catch (Exception e) {
                    report("membershipMessageReceived", e);
                }
            }
        };
    }

    private static void report(String callback, Exception e) {
        System.err.println("Erro no callback Spread " + callback + ": " + e);
        e.printStackTrace();
    }
}
