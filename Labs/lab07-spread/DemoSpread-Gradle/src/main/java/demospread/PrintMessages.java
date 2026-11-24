package demospread;

import spread.MembershipInfo;
import spread.SpreadGroup;
import spread.SpreadMessage;

import java.util.Arrays;

public class PrintMessages {

    static void MessageDetails(SpreadMessage msg)   {
        if(msg.isRegular())   {
            System.out.println("The received regular message is: " + SpreadMessages.text(msg));
            System.out.println("The message is a " + serviceType(msg) + " message.");
            SpreadGroup[] groups = msg.getGroups();
            System.out.println("Message was sent to " + groups.length + " groups:");
            printGroups(groups, "   ");
        } else if (msg.isMembership())            {
            // Received a MembershipInfo message
            printMembershipInfo(msg.getMembershipInfo());
        } else if (msg.isReject())  {
            // Received a Reject message
            System.out.println("Received a " + serviceType(msg) + " REJECTED message.");
            SpreadGroup[] groups = msg.getGroups();
            System.out.println("Sent by  " + msg.getSender() + " to " + groups.length + " groups:");
            printGroups(groups, "   ");
            System.out.println("The message is: " + SpreadMessages.text(msg));
        } else {
            System.out.println("Message is of unknown type: " + msg.getServiceType() );
        }
    }

    // tipo de serviço da mensagem (garantias de entrega e de ordem)
    private static String serviceType(SpreadMessage msg) {
        if(msg.isUnreliable()) return "UNRELIABLE";
        if(msg.isReliable())   return "RELIABLE";
        if(msg.isFifo())       return "FIFO";
        if(msg.isCausal())     return "CAUSAL";
        if(msg.isAgreed())     return "AGREED";
        if(msg.isSafe())       return "SAFE";
        return "UNKNOWN";
    }

    // mostra os grupos (ou membros), um por linha, com a indentação dada
    private static void printGroups(SpreadGroup[] groups, String indent) {
        for (SpreadGroup sp : groups)
            System.out.println(indent + sp);
    }

    public static void printMembershipInfo(MembershipInfo info)   {
        SpreadGroup group = info.getGroup();
        if(info.isRegularMembership()) {
            // getMembers() só é válido numa vista regular; nas outras devolve null
            SpreadGroup[] members = info.getMembers();
            System.out.println("REGULAR membership for group " + group +
                    " with " + members.length + " members:");
            printGroups(members, "\t");
            System.out.print("\tDue to: ");
            if(info.isCausedByJoin()) {
                System.out.println("JOIN of " + info.getJoined());
            } else if(info.isCausedByLeave()) {
                System.out.println("LEAVE of " + info.getLeft());
            } else if(info.isCausedByDisconnect()) {
                System.out.println("DISCONNECT of " + info.getDisconnected());
            } else if(info.isCausedByNetwork()) {
                System.out.println("NETWORK change");
                MembershipInfo.VirtualSynchronySet[] virtualSynchronySets = info.getVirtualSynchronySets();
                MembershipInfo.VirtualSynchronySet myVirtualSynchronySet = info.getMyVirtualSynchronySet();
                for( int i = 0 ; i < virtualSynchronySets.length ; ++i ) {
                    MembershipInfo.VirtualSynchronySet set = virtualSynchronySets[i];
                    System.out.print("\t\t");
                    if( set == myVirtualSynchronySet ) {
                        System.out.print( "(LOCAL) " );
                    } else {
                        System.out.print( "(OTHER) " );
                    }
                    System.out.println( "Virtual Synchrony Set " + i + " has " +
                            set.getSize() + " members:");
                    // getMembers() devolve o array interno, com a capacidade total:
                    // só as primeiras getSize() posições estão preenchidas
                    printGroups(Arrays.copyOf(set.getMembers(), set.getSize()), "\t\t\t");
                }
            }
        } else if(info.isTransition()) {
            // vista transitória (ex.: falha ou partição de um daemon):
            // a vista regular com os membros chega a seguir
            System.out.println("TRANSITIONAL membership for group " + group);
        } else if(info.isSelfLeave()) {
            System.out.println("SELF-LEAVE message for group " + group);
        }
    }
}
