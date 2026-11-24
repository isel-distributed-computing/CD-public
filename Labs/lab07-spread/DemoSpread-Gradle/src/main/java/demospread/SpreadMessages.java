package demospread;

import spread.SpreadConnection;
import spread.SpreadException;
import spread.SpreadMessage;

import java.nio.charset.StandardCharsets;

/**
 * Envio e leitura do conteúdo (texto) das mensagens Spread.
 *
 * O Spread só transporta bytes: o texto é codificado e descodificado aqui,
 * sempre em UTF-8, para que emissor e recetor usem a mesma codificação.
 */
public final class SpreadMessages {

    private SpreadMessages() { }

    // Envia o texto para um grupo; se o grupo for o grupo privado de um membro, é unicast.
    // Mensagens to tipo SAFE
    static void send(SpreadConnection connection, String groupName, String text) throws SpreadException {
        SpreadMessage msg = new SpreadMessage();
        msg.setSafe();
        msg.addGroup(groupName);
        msg.setData(text.getBytes(StandardCharsets.UTF_8));
        connection.multicast(msg);
    }

    // Conteúdo da mensagem recebida, como texto
    static String text(SpreadMessage msg) {
        return new String(msg.getData(), StandardCharsets.UTF_8);
    }
}
