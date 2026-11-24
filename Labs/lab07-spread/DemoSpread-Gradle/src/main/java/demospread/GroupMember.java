package demospread;

import spread.BasicMessageListener;
import spread.SpreadConnection;
import spread.SpreadException;
import spread.SpreadGroup;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GroupMember {

    private final SpreadConnection connection;
    private final SpreadGroup myPrivateGroup;
    // listeners registados na ligação, já embrulhados pelo SafeListener
    private final BasicMessageListener msgHandling;
    //private final AdvancedMessageListener advancedMsgHandling;

    private final Map<String, SpreadGroup> groupsBelonging=new HashMap<>();

    // Estabelece a ligação ao daemon. Os erros de ligação são lançados para
    // quem cria o membro decidir o que fazer (em vez de terminar a JVM aqui)
    public GroupMember(String memberName, String daemonIP, int port)
            throws SpreadException, UnknownHostException {
        this.connection = new SpreadConnection();
        connection.connect(InetAddress.getByName(daemonIP), port, memberName, false, true);
        myPrivateGroup=connection.getPrivateGroup();
        // SafeListener: uma exceção num callback não pode terminar a thread de receção do Spread
        this.msgHandling = SafeListener.of(new MessageHandling(connection,myPrivateGroup));
        connection.add(msgHandling);
        //advancedMsgHandling = SafeListener.of(new AdvancedMessageHandling()); connection.add(advancedMsgHandling);
    }

    public List<String> getNamesOfBelongingGroups()  {
        return new ArrayList<>(groupsBelonging.keySet());
    }

    // devolve false se o membro já pertence ao grupo
    public boolean JoinToGrupo(String groupName) throws SpreadException {
        if (groupsBelonging.containsKey(groupName)) {
            return false;
        }
        SpreadGroup newGroup=new SpreadGroup();
        newGroup.join(connection, groupName);
        groupsBelonging.put(groupName,newGroup);
        return true;
    }

    public void SendMessage(String groupToSend, String txtMessage) throws SpreadException {
        SpreadMessages.send(connection, groupToSend, txtMessage);
    }

    // devolve false se o membro não pertence ao grupo indicado
    public boolean groupLeave(String nameToLeave) throws SpreadException {
        SpreadGroup group=groupsBelonging.get(nameToLeave);
        if (group == null) {
            return false;
        }
        group.leave();
        groupsBelonging.remove(nameToLeave);
        return true;
    }

    public void close() throws SpreadException {
        // remove listener
        connection.remove(msgHandling);
        //connection.remove(advancedMsgHandling);
        // Disconnect.
        connection.disconnect();
    }

    public void sendUnicastMessage(String privateNameDestination, String daemonNameDestination, String msgUnicast) throws SpreadException {
        String destinationPrivateGroup="#"+privateNameDestination+"#"+daemonNameDestination;
        SpreadMessages.send(connection, destinationPrivateGroup, msgUnicast);  //UNICAST message to privateGroup
        System.out.println("Message sent to: "+ destinationPrivateGroup);
    }
}
