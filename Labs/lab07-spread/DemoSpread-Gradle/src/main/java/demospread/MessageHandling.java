package demospread;

import spread.BasicMessageListener;
import spread.SpreadConnection;
import spread.SpreadException;
import spread.SpreadGroup;
import spread.SpreadMessage;

public class MessageHandling implements BasicMessageListener {
    private final SpreadConnection connection;
    private final SpreadGroup myPrivateGroup;
    public MessageHandling(SpreadConnection connection, SpreadGroup privateGroup) {
        this.connection = connection;
        this.myPrivateGroup=privateGroup;
    }

    // As exceções não verificadas são apanhadas pelo SafeListener que embrulha este listener
    @Override
    public void messageReceived(SpreadMessage spreadMessage) {
        System.out.println("Message Received ThreadID=" + Thread.currentThread().threadId()+":");
        PrintMessages.MessageDetails(spreadMessage);
        if(spreadMessage.isRegular()) {
            // verificar se mensagem é unicast (enviada só para o meu PrivateGroup ou multicast para Spread grupo
            SpreadGroup[] msgGroups = spreadMessage.getGroups();
            if (msgGroups.length == 1 && msgGroups[0].equals(myPrivateGroup)) {
                // mensagem unicast
                SpreadGroup senderPrivateGroup = spreadMessage.getSender();
                String unicastMsg = SpreadMessages.text(spreadMessage);
                System.out.println("Unicast message from senderPrivateGroup=" + senderPrivateGroup);
                // process the unicast message, for instance reply to the sender if message ="privateRequest"
                if (unicastMsg.equalsIgnoreCase("privateRequest")) {
                    try {
                        SpreadMessages.send(connection, senderPrivateGroup.toString(),
                                "Reply from " + myPrivateGroup + ": I received your private request");
                    } catch (SpreadException e) {
                        System.err.println("Could not reply to " + senderPrivateGroup + ": " + e.getMessage());
                    }
                }
            } else { // message sent to a group
                // . . . to do Work, e.g. collect state from payload (data[])
            }
        }
    }
}

