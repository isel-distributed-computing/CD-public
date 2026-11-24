package demospread;

import spread.AdvancedMessageListener;
import spread.SpreadMessage;

// Alternativa ao MessageHandling: um callback para mensagens regulares e outro para membership
public class AdvancedMessageHandling implements AdvancedMessageListener {

    @Override
    public void regularMessageReceived(SpreadMessage spreadMessage) {
        System.out.println("Regular ThreadID:" + Thread.currentThread().threadId());
        System.out.println("The message is: " + SpreadMessages.text(spreadMessage));
    }

    @Override
    public void membershipMessageReceived(SpreadMessage spreadMessage) {
        System.out.println("MemberShip ThreadID:" + Thread.currentThread().threadId());
        PrintMessages.printMembershipInfo(spreadMessage.getMembershipInfo());
    }
}
